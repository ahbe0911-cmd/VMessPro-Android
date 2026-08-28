using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace VMessPro.Win
{
    public sealed class VpnEngine : IDisposable
    {
        private const string TunGuid = "{30E6A0AA-59A6-4A96-9D18-9B5F5C1A6E51}";
        private const string TunName = "VMessPro";
        private const string TunAddress = "192.168.123.1";
        private const string TunMask = "255.255.255.0";

        private readonly string _baseDir;
        private readonly string _coreDir;
        private readonly string _runtimeDir;
        private readonly string _xrayPath;
        private readonly string _tun2SocksPath;
        private readonly string _diagnosticPath;

        private Process _xray;
        private Process _tun2Socks;
        private string _serverIp;
        private string _gateway;
        private int _physicalIfIndex;
        private int _tunIfIndex;
        private string _tunInterfaceId;
        private string _tunInterfaceName;
        private long _previousRx;
        private long _previousTx;
        private DateTime _previousTrafficAt;

        public WinVpnState State { get; private set; } = WinVpnState.Disconnected;
        public ConnectionSnapshot Snapshot { get; private set; } = new ConnectionSnapshot
        {
            State = WinVpnState.Disconnected,
            Message = "آماده برای اتصال"
        };

        public event Action<ConnectionSnapshot> SnapshotChanged;

        public VpnEngine()
        {
            _baseDir = AppDomain.CurrentDomain.BaseDirectory;
            _coreDir = Path.Combine(_baseDir, "core");
            _runtimeDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "VMessPro", "run");
            Directory.CreateDirectory(_runtimeDir);
            _xrayPath = Path.Combine(_coreDir, "xray.exe");
            _tun2SocksPath = Path.Combine(_coreDir, "tun2socks.exe");
            _diagnosticPath = Path.Combine(_runtimeDir, "windows-tunnel.log");
        }

        public bool CoreFilesPresent
        {
            get
            {
                return File.Exists(_xrayPath) &&
                       File.Exists(_tun2SocksPath) &&
                       File.Exists(Path.Combine(_coreDir, "wintun.dll"));
            }
        }

        public async Task ConnectAsync(VpnProfile profile)
        {
            if (profile == null) throw new ArgumentNullException(nameof(profile));
            if (!CoreFilesPresent) throw new FileNotFoundException("فایل‌های Core ویندوز کامل نیستند.");

            await DisconnectInternalAsync(false).ConfigureAwait(false);
            CleanupStaleRoutes();
            ResetDiagnostics();
            SetState(WinVpnState.Preparing, "در حال آماده‌سازی Xray…");

            try
            {
                var parsed = EnsureMetadata(profile);
                var endpoint = await ResolveIpv4Async(parsed.Host).ConfigureAwait(false);
                _serverIp = endpoint.ToString();

                var route = FindDefaultRoute();
                _gateway = route.Gateway;
                _physicalIfIndex = route.InterfaceIndex;
                AppendDiagnostic("Primary: " + route.InterfaceName + " / " + _gateway + " / if=" + _physicalIfIndex);
                AppendDiagnostic("Endpoint: " + _serverIp);

                var socksPort = GetFreePort();
                var httpPort = GetFreePort();
                var configPath = Path.Combine(_runtimeDir, "active-xray.json");
                File.WriteAllText(
                    configPath,
                    XrayConfigBuilder.BuildJson(parsed, socksPort, httpPort, _serverIp),
                    new UTF8Encoding(false));

                SetState(WinVpnState.Connecting, "در حال راه‌اندازی Xray…");
                _xray = StartHidden(_xrayPath, "run -c " + Quote(configPath), _coreDir);
                await WaitForTcpPortAsync(httpPort, 5500).ConfigureAwait(false);

                SetState(WinVpnState.Verifying, "در حال تست واقعی کانفیگ…");
                var proxyLatency = await VerifyThroughHttpProxyAsync(httpPort).ConfigureAwait(false);
                profile.LatencyMs = proxyLatency;
                profile.LastSuccess = true;
                AppendDiagnostic("Xray proxy verified: " + proxyLatency + " ms");

                var beforeInterfaces = new HashSet<string>(
                    NetworkInterface.GetAllNetworkInterfaces().Select(n => n.Id),
                    StringComparer.OrdinalIgnoreCase);

                SetState(WinVpnState.Connecting, "در حال ساخت تونل ویندوز…");
                _tun2Socks = StartTun2Socks(
                    "--device " + Quote("tun://" + TunName + "?guid=" + TunGuid) +
                    " --proxy " + Quote("socks5://127.0.0.1:" + socksPort) +
                    " --mtu 1500 --loglevel info");

                var tun = await WaitForTunInterfaceAsync(beforeInterfaces, 9000).ConfigureAwait(false);
                var ipv4 = tun.GetIPProperties().GetIPv4Properties();
                if (ipv4 == null) throw new InvalidOperationException("رابط Wintun فاقد IPv4 است.");

                _tunIfIndex = ipv4.Index;
                _tunInterfaceId = tun.Id;
                _tunInterfaceName = tun.Name;
                AppendDiagnostic("TUN: " + _tunInterfaceName + " / id=" + _tunInterfaceId + " / if=" + _tunIfIndex);

                ConfigureTunInterface();

                // Keep the Xray server outside the VPN before the default route changes.
                AddRoute(_serverIp, "255.255.255.255", _gateway, 1, _physicalIfIndex);

                // Use the route model documented by tun2socks for Windows: one default route
                // attached directly to the Wintun interface. This is more reliable on Windows 8.1
                // than the previous pair of 0/1 + 128/1 route.exe entries.
                AddTunDefaultRoute();

                await WaitForTunRouteReadyAsync().ConfigureAwait(false);
                EnsureProcessAlive(_tun2Socks, "tun2socks");
                FlushDnsQuietly();

                SetState(WinVpnState.Verifying, "در حال بررسی ترافیک واقعی ویندوز…");
                var systemLatency = await VerifySystemTunnelAsync().ConfigureAwait(false);
                var publicIp = await FetchPublicIpAsync().ConfigureAwait(false);
                profile.LatencyMs = systemLatency;

                ResetTrafficBaseline();
                SetState(
                    WinVpnState.Connected,
                    "متصل و تأییدشده",
                    systemLatency,
                    publicIp,
                    DateTime.Now);
                AppendDiagnostic("System tunnel verified: " + systemLatency + " ms / IP=" + publicIp);
            }
            catch (Exception ex)
            {
                AppendDiagnostic("ERROR: " + ex);
                await DisconnectInternalAsync(false).ConfigureAwait(false);
                SetState(WinVpnState.Error, "خطا: " + ex.Message);
                throw;
            }
        }

        public async Task DisconnectAsync()
        {
            await DisconnectInternalAsync(true).ConfigureAwait(false);
        }

        public async Task<int> TestProfileAsync(VpnProfile profile)
        {
            if (profile == null) throw new ArgumentNullException(nameof(profile));
            if (State == WinVpnState.Connected || State == WinVpnState.Connecting || State == WinVpnState.Verifying)
                throw new InvalidOperationException("برای تست مستقل سرورها ابتدا VPN را قطع کنید.");

            var parsed = EnsureMetadata(profile);
            var endpoint = await ResolveIpv4Async(parsed.Host).ConfigureAwait(false);
            var socksPort = GetFreePort();
            var httpPort = GetFreePort();
            var configPath = Path.Combine(_runtimeDir, "test-" + Guid.NewGuid().ToString("N") + ".json");
            File.WriteAllText(
                configPath,
                XrayConfigBuilder.BuildJson(parsed, socksPort, httpPort, endpoint.ToString()),
                new UTF8Encoding(false));

            Process process = null;
            try
            {
                process = StartHidden(_xrayPath, "run -c " + Quote(configPath), _coreDir);
                await WaitForTcpPortAsync(httpPort, 5500).ConfigureAwait(false);
                var latency = await VerifyThroughHttpProxyAsync(httpPort).ConfigureAwait(false);
                profile.LatencyMs = latency;
                profile.LastSuccess = true;
                return latency;
            }
            catch
            {
                profile.LastSuccess = false;
                throw;
            }
            finally
            {
                KillQuietly(process);
                TryDelete(configPath);
            }
        }

        public async Task<List<VpnProfile>> NormalizeImportAsync(string input)
        {
            if (string.IsNullOrWhiteSpace(input)) return new List<VpnProfile>();
            var text = input.Trim();

            if ((text.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
                 text.StartsWith("https://", StringComparison.OrdinalIgnoreCase)) &&
                text.IndexOf('\n') < 0)
            {
                using (var client = new WebClient())
                {
                    client.Headers[HttpRequestHeader.UserAgent] = "VMessPro-Windows/0.6.2";
                    text = await client.DownloadStringTaskAsync(text).ConfigureAwait(false);
                }
            }

            var links = XrayConfigBuilder.ExtractShareLinks(text);
            var profiles = new List<VpnProfile>();
            foreach (var link in links)
            {
                try
                {
                    var parsed = XrayConfigBuilder.Parse(link);
                    profiles.Add(new VpnProfile
                    {
                        Id = ProfileStore.StableId(link),
                        RawLink = link,
                        Name = parsed.Name,
                        Protocol = parsed.Protocol,
                        Host = parsed.Host
                    });
                }
                catch
                {
                    // Invalid entries are intentionally skipped instead of becoming fake profiles.
                }
            }
            return profiles;
        }

        public ConnectionSnapshot PollTraffic()
        {
            if (State != WinVpnState.Connected || string.IsNullOrWhiteSpace(_tunInterfaceId)) return Snapshot;
            try
            {
                var nic = NetworkInterface.GetAllNetworkInterfaces().FirstOrDefault(n =>
                    string.Equals(n.Id, _tunInterfaceId, StringComparison.OrdinalIgnoreCase));
                if (nic == null) return Snapshot;
                var stats = nic.GetIPv4Statistics();
                var now = DateTime.UtcNow;
                var elapsed = Math.Max(0.2, (now - _previousTrafficAt).TotalSeconds);
                var rx = stats.BytesReceived;
                var tx = stats.BytesSent;
                Snapshot.DownloadMbps = Math.Max(0, rx - _previousRx) * 8.0 / elapsed / 1000000.0;
                Snapshot.UploadMbps = Math.Max(0, tx - _previousTx) * 8.0 / elapsed / 1000000.0;
                _previousRx = rx;
                _previousTx = tx;
                _previousTrafficAt = now;
                SnapshotChanged?.Invoke(Snapshot);
            }
            catch { }
            return Snapshot;
        }

        private ParsedShare EnsureMetadata(VpnProfile profile)
        {
            var parsed = XrayConfigBuilder.Parse(profile.RawLink);
            profile.Protocol = parsed.Protocol;
            profile.Name = parsed.Name;
            profile.Host = parsed.Host;
            return parsed;
        }

        private async Task DisconnectInternalAsync(bool broadcast)
        {
            DeleteTunDefaultRouteQuietly();

            // Clean routes created by the previous 0.6.1 implementation as well.
            DeleteRoute("0.0.0.0", "128.0.0.0", TunAddress, _tunIfIndex);
            DeleteRoute("128.0.0.0", "128.0.0.0", TunAddress, _tunIfIndex);

            if (!string.IsNullOrWhiteSpace(_serverIp) && !string.IsNullOrWhiteSpace(_gateway))
                DeleteRoute(_serverIp, "255.255.255.255", _gateway, _physicalIfIndex);

            KillQuietly(_tun2Socks);
            KillQuietly(_xray);
            _tun2Socks = null;
            _xray = null;
            _serverIp = null;
            _gateway = null;
            _physicalIfIndex = 0;
            _tunIfIndex = 0;
            _tunInterfaceId = null;
            _tunInterfaceName = null;
            await Task.Delay(100).ConfigureAwait(false);
            if (broadcast) SetState(WinVpnState.Disconnected, "قطع • آماده برای اتصال");
        }

        private void ConfigureTunInterface()
        {
            RunNetsh("interface ipv4 set address name=" + Quote(_tunInterfaceName) +
                     " source=static address=" + TunAddress + " mask=" + TunMask + " gateway=none");
            RunNetsh("interface ipv4 set interface interface=" + Quote(_tunInterfaceName) + " metric=1");
            RunNetsh("interface ipv4 set dnsservers name=" + Quote(_tunInterfaceName) +
                     " source=static address=1.1.1.1 register=none validate=no");
        }

        private void AddTunDefaultRoute()
        {
            DeleteTunDefaultRouteQuietly();
            var result = RunProcessCapture(
                "netsh.exe",
                "interface ipv4 add route 0.0.0.0/0 " + Quote(_tunInterfaceName) + " " + TunAddress + " metric=1",
                Environment.SystemDirectory,
                8000);
            if (result.ExitCode != 0)
                throw new InvalidOperationException("مسیر پیش‌فرض Wintun ساخته نشد: " + result.Error + " " + result.Output);
        }

        private void DeleteTunDefaultRouteQuietly()
        {
            if (string.IsNullOrWhiteSpace(_tunInterfaceName))
            {
                // Best-effort cleanup for a previous crashed process. The persistent adapter is named VMessPro.
                var existing = FindTunInterfaceByName();
                if (existing != null) _tunInterfaceName = existing.Name;
            }

            if (string.IsNullOrWhiteSpace(_tunInterfaceName)) return;
            try
            {
                RunProcessCapture(
                    "netsh.exe",
                    "interface ipv4 delete route 0.0.0.0/0 " + Quote(_tunInterfaceName) + " " + TunAddress,
                    Environment.SystemDirectory,
                    5000);
            }
            catch { }
        }

        private void CleanupStaleRoutes()
        {
            DeleteRoute("0.0.0.0", "128.0.0.0", TunAddress, 0);
            DeleteRoute("128.0.0.0", "128.0.0.0", TunAddress, 0);
            DeleteTunDefaultRouteQuietly();
            _tunInterfaceName = null;
        }

        private async Task WaitForTunRouteReadyAsync()
        {
            var sw = Stopwatch.StartNew();
            while (sw.ElapsedMilliseconds < 4500)
            {
                EnsureProcessAlive(_tun2Socks, "tun2socks");
                var table = RunProcessCapture("route.exe", "PRINT -4", Environment.SystemDirectory, 5000);
                if (table.ExitCode == 0 &&
                    table.Output.IndexOf("0.0.0.0", StringComparison.OrdinalIgnoreCase) >= 0 &&
                    table.Output.IndexOf(TunAddress, StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    await Task.Delay(450).ConfigureAwait(false);
                    return;
                }
                await Task.Delay(180).ConfigureAwait(false);
            }
            throw new InvalidOperationException("Route جدول ویندوز برای Wintun آماده نشد.");
        }

        private async Task<int> VerifyThroughHttpProxyAsync(int httpPort)
        {
            Exception last = null;
            foreach (var endpoint in new[]
            {
                "https://www.gstatic.com/generate_204",
                "https://cp.cloudflare.com/generate_204"
            })
            {
                try
                {
                    var sw = Stopwatch.StartNew();
                    var request = (HttpWebRequest)WebRequest.Create(endpoint);
                    request.Proxy = new WebProxy("http://127.0.0.1:" + httpPort);
                    request.Timeout = 5000;
                    request.ReadWriteTimeout = 5000;
                    request.AllowAutoRedirect = false;
                    request.KeepAlive = false;
                    request.UserAgent = "VMessPro-Windows/0.6.2";
                    using (var response = (HttpWebResponse)await request.GetResponseAsync().ConfigureAwait(false))
                    {
                        sw.Stop();
                        var code = (int)response.StatusCode;
                        if (code == 204 || (code >= 200 && code < 400))
                            return Math.Max(1, (int)sw.ElapsedMilliseconds);
                    }
                }
                catch (Exception ex) { last = ex; }
            }
            throw new InvalidOperationException("Xray اجرا شد ولی HTTPS واقعی از کانفیگ عبور نکرد.", last);
        }

        private async Task<int> VerifySystemTunnelAsync()
        {
            Exception last = null;
            // First endpoint is an IPv4 literal so DNS cannot hide a route problem.
            foreach (var endpoint in new[]
            {
                "https://1.1.1.1/cdn-cgi/trace",
                "https://www.gstatic.com/generate_204",
                "https://cp.cloudflare.com/generate_204"
            })
            {
                try
                {
                    EnsureProcessAlive(_tun2Socks, "tun2socks");
                    var sw = Stopwatch.StartNew();
                    var request = (HttpWebRequest)WebRequest.Create(endpoint);
                    request.Proxy = null;
                    request.Timeout = 6000;
                    request.ReadWriteTimeout = 6000;
                    request.AllowAutoRedirect = false;
                    request.KeepAlive = false;
                    request.UserAgent = "VMessPro-Windows/0.6.2";
                    using (var response = (HttpWebResponse)await request.GetResponseAsync().ConfigureAwait(false))
                    {
                        sw.Stop();
                        var code = (int)response.StatusCode;
                        if (code >= 200 && code < 400)
                            return Math.Max(1, (int)sw.ElapsedMilliseconds);
                    }
                }
                catch (Exception ex)
                {
                    last = ex;
                    AppendDiagnostic("Verify failed " + endpoint + ": " + ex.Message);
                }
            }

            var routeDump = RunProcessCapture("route.exe", "PRINT -4", Environment.SystemDirectory, 6000);
            AppendDiagnostic("ROUTE TABLE:\r\n" + routeDump.Output);
            throw new InvalidOperationException(
                "تونل ساخته شد اما Route ویندوز عبور ترافیک را تأیید نکرد. فایل تشخیص در %LOCALAPPDATA%\\VMessPro\\run\\windows-tunnel.log ذخیره شد.",
                last);
        }

        private async Task<string> FetchPublicIpAsync()
        {
            foreach (var endpoint in new[] { "https://api.ipify.org", "https://checkip.amazonaws.com" })
            {
                try
                {
                    var request = (HttpWebRequest)WebRequest.Create(endpoint);
                    request.Proxy = null;
                    request.Timeout = 5000;
                    request.ReadWriteTimeout = 5000;
                    request.KeepAlive = false;
                    request.UserAgent = "VMessPro-Windows/0.6.2";
                    using (var response = (HttpWebResponse)await request.GetResponseAsync().ConfigureAwait(false))
                    using (var reader = new StreamReader(response.GetResponseStream()))
                    {
                        var text = (await reader.ReadToEndAsync().ConfigureAwait(false)).Trim();
                        if (!string.IsNullOrWhiteSpace(text)) return text;
                    }
                }
                catch { }
            }
            return "—";
        }

        private async Task WaitForTcpPortAsync(int port, int timeoutMs)
        {
            var started = Stopwatch.StartNew();
            Exception last = null;
            while (started.ElapsedMilliseconds < timeoutMs)
            {
                try
                {
                    using (var client = new TcpClient())
                    {
                        var connect = client.ConnectAsync(IPAddress.Loopback, port);
                        var completed = await Task.WhenAny(connect, Task.Delay(180)).ConfigureAwait(false);
                        if (completed == connect && client.Connected) return;
                    }
                }
                catch (Exception ex) { last = ex; }
                await Task.Delay(80).ConfigureAwait(false);
            }
            throw new TimeoutException("Xray local proxy آماده نشد.", last);
        }

        private async Task<NetworkInterface> WaitForTunInterfaceAsync(HashSet<string> before, int timeoutMs)
        {
            var sw = Stopwatch.StartNew();
            while (sw.ElapsedMilliseconds < timeoutMs)
            {
                EnsureProcessAlive(_tun2Socks, "tun2socks");
                var all = NetworkInterface.GetAllNetworkInterfaces();

                var found = all.FirstOrDefault(n =>
                    string.Equals(n.Name, TunName, StringComparison.OrdinalIgnoreCase));

                if (found == null)
                {
                    found = all.FirstOrDefault(n =>
                        !before.Contains(n.Id) &&
                        ((n.Description ?? string.Empty).IndexOf("Wintun", StringComparison.OrdinalIgnoreCase) >= 0 ||
                         (n.Name ?? string.Empty).IndexOf(TunName, StringComparison.OrdinalIgnoreCase) >= 0));
                }

                if (found != null) return found;
                await Task.Delay(120).ConfigureAwait(false);
            }
            throw new TimeoutException("رابط اختصاصی Wintun برنامه ساخته نشد.");
        }

        private NetworkInterface FindTunInterfaceByName()
        {
            try
            {
                return NetworkInterface.GetAllNetworkInterfaces().FirstOrDefault(n =>
                    string.Equals(n.Name, TunName, StringComparison.OrdinalIgnoreCase) ||
                    (n.Name ?? string.Empty).IndexOf(TunName, StringComparison.OrdinalIgnoreCase) >= 0);
            }
            catch { return null; }
        }

        private DefaultRoute FindDefaultRoute()
        {
            var output = RunProcessCapture("route.exe", "PRINT -4", Environment.SystemDirectory, 6000).Output;
            var matches = Regex.Matches(
                output,
                @"(?m)^\s*0\.0\.0\.0\s+0\.0\.0\.0\s+(?<gw>\d{1,3}(?:\.\d{1,3}){3})\s+(?<iface>\d{1,3}(?:\.\d{1,3}){3})\s+(?<metric>\d+)\s*$");
            if (matches.Count == 0) throw new InvalidOperationException("Default IPv4 route پیدا نشد.");

            var candidates = matches.Cast<Match>()
                .Select(m => new
                {
                    Gateway = m.Groups["gw"].Value,
                    InterfaceIp = m.Groups["iface"].Value,
                    Metric = int.Parse(m.Groups["metric"].Value)
                })
                .Where(v => v.Gateway != TunAddress)
                .OrderBy(v => v.Metric)
                .ToList();

            foreach (var item in candidates)
            {
                var nic = NetworkInterface.GetAllNetworkInterfaces().FirstOrDefault(n =>
                    n.OperationalStatus == OperationalStatus.Up &&
                    !string.Equals(n.Name, TunName, StringComparison.OrdinalIgnoreCase) &&
                    n.GetIPProperties().UnicastAddresses.Any(a =>
                        a.Address.AddressFamily == AddressFamily.InterNetwork &&
                        a.Address.ToString() == item.InterfaceIp));
                if (nic == null) continue;
                var ipv4 = nic.GetIPProperties().GetIPv4Properties();
                if (ipv4 == null) continue;
                return new DefaultRoute
                {
                    Gateway = item.Gateway,
                    InterfaceIndex = ipv4.Index,
                    InterfaceName = nic.Name
                };
            }
            throw new InvalidOperationException("رابط شبکه اصلی شناسایی نشد.");
        }

        private async Task<IPAddress> ResolveIpv4Async(string host)
        {
            return await Task.Run(() =>
            {
                IPAddress literal;
                if (IPAddress.TryParse(host, out literal) && literal.AddressFamily == AddressFamily.InterNetwork)
                    return literal;
                var address = Dns.GetHostAddresses(host)
                    .FirstOrDefault(a => a.AddressFamily == AddressFamily.InterNetwork);
                if (address == null) throw new InvalidOperationException("IPv4 سرور Resolve نشد: " + host);
                return address;
            }).ConfigureAwait(false);
        }

        private void ResetTrafficBaseline()
        {
            try
            {
                var nic = NetworkInterface.GetAllNetworkInterfaces().FirstOrDefault(n =>
                    string.Equals(n.Id, _tunInterfaceId, StringComparison.OrdinalIgnoreCase));
                if (nic != null)
                {
                    var stats = nic.GetIPv4Statistics();
                    _previousRx = stats.BytesReceived;
                    _previousTx = stats.BytesSent;
                }
            }
            catch
            {
                _previousRx = 0;
                _previousTx = 0;
            }
            _previousTrafficAt = DateTime.UtcNow;
        }

        private void SetState(
            WinVpnState state,
            string message,
            int? latency = null,
            string publicIp = null,
            DateTime? since = null)
        {
            State = state;
            Snapshot.State = state;
            Snapshot.Message = message;
            if (latency.HasValue) Snapshot.LatencyMs = latency;
            if (publicIp != null) Snapshot.PublicIp = publicIp;
            if (since.HasValue) Snapshot.ConnectedSince = since.Value;
            if (state == WinVpnState.Disconnected || state == WinVpnState.Error)
            {
                Snapshot.DownloadMbps = 0;
                Snapshot.UploadMbps = 0;
                if (state == WinVpnState.Disconnected) Snapshot.PublicIp = null;
            }
            SnapshotChanged?.Invoke(Snapshot);
        }

        private static int GetFreePort()
        {
            var listener = new TcpListener(IPAddress.Loopback, 0);
            listener.Start();
            try { return ((IPEndPoint)listener.LocalEndpoint).Port; }
            finally { listener.Stop(); }
        }

        private static Process StartHidden(string file, string args, string workDir)
        {
            var start = new ProcessStartInfo(file, args)
            {
                WorkingDirectory = workDir,
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden
            };
            var process = Process.Start(start);
            if (process == null) throw new InvalidOperationException("Process start failed: " + file);
            return process;
        }

        private Process StartTun2Socks(string args)
        {
            var start = new ProcessStartInfo(_tun2SocksPath, args)
            {
                WorkingDirectory = _coreDir,
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8
            };
            var process = new Process { StartInfo = start, EnableRaisingEvents = true };
            process.OutputDataReceived += (s, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) AppendDiagnostic("tun2socks: " + e.Data); };
            process.ErrorDataReceived += (s, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) AppendDiagnostic("tun2socks! " + e.Data); };
            if (!process.Start()) throw new InvalidOperationException("tun2socks اجرا نشد.");
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            return process;
        }

        private static ProcessResult RunProcessCapture(string file, string args, string workDir, int timeoutMs)
        {
            var start = new ProcessStartInfo(file, args)
            {
                WorkingDirectory = workDir,
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8
            };
            using (var process = Process.Start(start))
            {
                if (process == null) return new ProcessResult { ExitCode = -1, Error = "start failed" };
                var output = process.StandardOutput.ReadToEnd();
                var error = process.StandardError.ReadToEnd();
                if (!process.WaitForExit(timeoutMs))
                {
                    try { process.Kill(); } catch { }
                    return new ProcessResult { ExitCode = -2, Output = output, Error = "timeout: " + error };
                }
                return new ProcessResult { ExitCode = process.ExitCode, Output = output, Error = error };
            }
        }

        private static void RunNetsh(string args)
        {
            var result = RunProcessCapture("netsh.exe", args, Environment.SystemDirectory, 8000);
            if (result.ExitCode != 0)
                throw new InvalidOperationException("netsh: " + result.Error + " " + result.Output);
        }

        private static void AddRoute(string destination, string mask, string gateway, int metric, int ifIndex)
        {
            var args = "ADD " + destination + " MASK " + mask + " " + gateway + " METRIC " + metric;
            if (ifIndex > 0) args += " IF " + ifIndex;
            var result = RunProcessCapture("route.exe", args, Environment.SystemDirectory, 6000);
            if (result.ExitCode != 0)
            {
                DeleteRoute(destination, mask, gateway, ifIndex);
                result = RunProcessCapture("route.exe", args, Environment.SystemDirectory, 6000);
                if (result.ExitCode != 0)
                    throw new InvalidOperationException("route add failed: " + result.Output + result.Error);
            }
        }

        private static void DeleteRoute(string destination, string mask, string gateway, int ifIndex)
        {
            if (string.IsNullOrWhiteSpace(destination) || string.IsNullOrWhiteSpace(mask) || string.IsNullOrWhiteSpace(gateway))
                return;
            var args = "DELETE " + destination + " MASK " + mask + " " + gateway;
            if (ifIndex > 0) args += " IF " + ifIndex;
            try { RunProcessCapture("route.exe", args, Environment.SystemDirectory, 4000); } catch { }
        }

        private static void EnsureProcessAlive(Process process, string name)
        {
            if (process == null) throw new InvalidOperationException(name + " اجرا نشده است.");
            try
            {
                if (process.HasExited)
                    throw new InvalidOperationException(name + " متوقف شد (ExitCode=" + process.ExitCode + ").");
            }
            catch (InvalidOperationException) { throw; }
            catch (Exception ex) { throw new InvalidOperationException("وضعیت " + name + " قابل بررسی نیست.", ex); }
        }

        private static void KillQuietly(Process process)
        {
            if (process == null) return;
            try
            {
                if (!process.HasExited)
                {
                    process.Kill();
                    process.WaitForExit(2500);
                }
            }
            catch { }
            try { process.Dispose(); } catch { }
        }

        private static void TryDelete(string path)
        {
            try
            {
                if (!string.IsNullOrWhiteSpace(path) && File.Exists(path)) File.Delete(path);
            }
            catch { }
        }

        private void FlushDnsQuietly()
        {
            try { RunProcessCapture("ipconfig.exe", "/flushdns", Environment.SystemDirectory, 5000); } catch { }
        }

        private void ResetDiagnostics()
        {
            try { File.WriteAllText(_diagnosticPath, "VMess Pro Windows tunnel diagnostics\r\n" + DateTime.Now + "\r\n", Encoding.UTF8); }
            catch { }
        }

        private void AppendDiagnostic(string value)
        {
            try { File.AppendAllText(_diagnosticPath, DateTime.Now.ToString("HH:mm:ss.fff") + "  " + value + "\r\n", Encoding.UTF8); }
            catch { }
        }

        private static string Quote(string value)
        {
            return "\"" + (value ?? string.Empty).Replace("\"", "\\\"") + "\"";
        }

        public void Dispose()
        {
            try { DisconnectInternalAsync(false).GetAwaiter().GetResult(); } catch { }
        }

        private sealed class ProcessResult
        {
            public int ExitCode { get; set; }
            public string Output { get; set; }
            public string Error { get; set; }
        }

        private sealed class DefaultRoute
        {
            public string Gateway { get; set; }
            public int InterfaceIndex { get; set; }
            public string InterfaceName { get; set; }
        }
    }
}
