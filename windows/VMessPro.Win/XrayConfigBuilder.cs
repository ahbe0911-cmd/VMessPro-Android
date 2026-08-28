using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Text;
using System.Web;
using System.Web.Script.Serialization;

namespace VMessPro.Win
{
    internal sealed class ParsedShare
    {
        public string RawLink { get; set; }
        public string Protocol { get; set; }
        public string Name { get; set; }
        public string Host { get; set; }
        public int Port { get; set; }
        public Dictionary<string, object> Outbound { get; set; }
    }

    internal static class XrayConfigBuilder
    {
        private static readonly JavaScriptSerializer Json = new JavaScriptSerializer { MaxJsonLength = 16 * 1024 * 1024 };

        public static ParsedShare Parse(string rawLink)
        {
            if (string.IsNullOrWhiteSpace(rawLink)) throw new InvalidOperationException("کانفیگ خالی است.");
            var raw = rawLink.Trim();
            if (raw.StartsWith("vmess://", StringComparison.OrdinalIgnoreCase)) return ParseVmess(raw);
            if (raw.StartsWith("vless://", StringComparison.OrdinalIgnoreCase)) return ParseVless(raw);
            if (raw.StartsWith("trojan://", StringComparison.OrdinalIgnoreCase)) return ParseTrojan(raw);
            throw new NotSupportedException("در Windows 8.1 فعلاً VMess / VLESS / Reality / Trojan پشتیبانی می‌شوند.");
        }

        public static IList<string> ExtractShareLinks(string text)
        {
            if (string.IsNullOrWhiteSpace(text)) return new List<string>();
            var normalized = DecodeSubscriptionIfNeeded(text.Trim());
            var links = new List<string>();
            foreach (var line in normalized.Replace("\r", "\n").Split(new[] { '\n', ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries))
            {
                var candidate = line.Trim().Trim(',', ';');
                if (candidate.StartsWith("vmess://", StringComparison.OrdinalIgnoreCase) ||
                    candidate.StartsWith("vless://", StringComparison.OrdinalIgnoreCase) ||
                    candidate.StartsWith("trojan://", StringComparison.OrdinalIgnoreCase))
                {
                    if (!links.Contains(candidate, StringComparer.Ordinal)) links.Add(candidate);
                }
            }
            return links;
        }

        public static string BuildJson(ParsedShare share, int socksPort, int httpPort, string endpointIp)
        {
            if (share == null || share.Outbound == null) throw new InvalidOperationException("کانفیگ Xray معتبر نیست.");
            var outbound = DeepClone(share.Outbound);
            outbound["tag"] = "proxy";
            if (!string.IsNullOrWhiteSpace(endpointIp)) ReplaceEndpoint(outbound, endpointIp);

            var root = new Dictionary<string, object>
            {
                ["log"] = new Dictionary<string, object> { ["loglevel"] = "warning" },
                ["inbounds"] = new object[]
                {
                    new Dictionary<string, object>
                    {
                        ["listen"] = "127.0.0.1",
                        ["port"] = socksPort,
                        ["protocol"] = "socks",
                        ["tag"] = "socks-in",
                        ["settings"] = new Dictionary<string, object> { ["auth"] = "noauth", ["udp"] = true },
                        ["sniffing"] = new Dictionary<string, object> { ["enabled"] = true, ["destOverride"] = new object[] { "http", "tls", "quic" } }
                    },
                    new Dictionary<string, object>
                    {
                        ["listen"] = "127.0.0.1",
                        ["port"] = httpPort,
                        ["protocol"] = "http",
                        ["tag"] = "http-in",
                        ["settings"] = new Dictionary<string, object>(),
                        ["sniffing"] = new Dictionary<string, object> { ["enabled"] = true, ["destOverride"] = new object[] { "http", "tls" } }
                    }
                },
                ["outbounds"] = new object[]
                {
                    outbound,
                    new Dictionary<string, object> { ["protocol"] = "freedom", ["tag"] = "direct" },
                    new Dictionary<string, object> { ["protocol"] = "blackhole", ["tag"] = "block" }
                },
                ["routing"] = new Dictionary<string, object>
                {
                    ["domainStrategy"] = "AsIs",
                    ["rules"] = new object[]
                    {
                        new Dictionary<string, object>
                        {
                            ["type"] = "field",
                            ["inboundTag"] = new object[] { "socks-in", "http-in" },
                            ["outboundTag"] = "proxy"
                        }
                    }
                }
            };
            return Json.Serialize(root);
        }

        private static ParsedShare ParseVmess(string raw)
        {
            var encoded = raw.Substring("vmess://".Length).Trim();
            var decoded = Encoding.UTF8.GetString(DecodeBase64Flexible(encoded));
            var map = Json.Deserialize<Dictionary<string, object>>(decoded);
            if (map == null) throw new InvalidOperationException("VMess JSON معتبر نیست.");

            var host = S(map, "add");
            var port = I(map, "port", 0);
            var id = S(map, "id");
            if (string.IsNullOrWhiteSpace(host) || port <= 0 || string.IsNullOrWhiteSpace(id))
                throw new InvalidOperationException("VMess فاقد host/port/id است.");

            var network = NormalizeNetwork(S(map, "net"));
            var security = S(map, "tls");
            var stream = BuildStreamSettings(
                network,
                security,
                S(map, "sni"),
                S(map, "fp"),
                S(map, "host"),
                S(map, "path"),
                S(map, "type"),
                S(map, "alpn"),
                null,
                null,
                null,
                null);

            var user = new Dictionary<string, object>
            {
                ["id"] = id,
                ["alterId"] = I(map, "aid", 0),
                ["security"] = string.IsNullOrWhiteSpace(S(map, "scy")) ? "auto" : S(map, "scy")
            };
            var outbound = new Dictionary<string, object>
            {
                ["protocol"] = "vmess",
                ["settings"] = new Dictionary<string, object>
                {
                    ["vnext"] = new object[]
                    {
                        new Dictionary<string, object>
                        {
                            ["address"] = host,
                            ["port"] = port,
                            ["users"] = new object[] { user }
                        }
                    }
                },
                ["streamSettings"] = stream
            };
            return new ParsedShare
            {
                RawLink = raw,
                Protocol = "VMESS",
                Name = DecodeName(S(map, "ps"), "VMESS • " + host),
                Host = host,
                Port = port,
                Outbound = outbound
            };
        }

        private static ParsedShare ParseVless(string raw)
        {
            var uri = new Uri(raw);
            var id = Uri.UnescapeDataString(uri.UserInfo ?? string.Empty);
            var host = uri.Host;
            var port = uri.Port;
            if (string.IsNullOrWhiteSpace(id) || string.IsNullOrWhiteSpace(host) || port <= 0)
                throw new InvalidOperationException("VLESS فاقد id/host/port است.");

            var query = HttpUtility.ParseQueryString(uri.Query);
            var network = NormalizeNetwork(query["type"]);
            var security = query["security"] ?? string.Empty;
            var sni = First(query["sni"], query["serverName"]);
            var fp = First(query["fp"], query["fingerprint"]);
            var streamHost = First(query["host"], query["authority"]);
            var path = First(query["path"], query["serviceName"]);
            var headerType = query["headerType"];
            var alpn = query["alpn"];
            var publicKey = First(query["pbk"], query["publicKey"]);
            var shortId = First(query["sid"], query["shortId"]);
            var spiderX = First(query["spx"], query["spiderX"]);

            var user = new Dictionary<string, object>
            {
                ["id"] = id,
                ["encryption"] = string.IsNullOrWhiteSpace(query["encryption"]) ? "none" : query["encryption"]
            };
            if (!string.IsNullOrWhiteSpace(query["flow"])) user["flow"] = query["flow"];

            var outbound = new Dictionary<string, object>
            {
                ["protocol"] = "vless",
                ["settings"] = new Dictionary<string, object>
                {
                    ["vnext"] = new object[]
                    {
                        new Dictionary<string, object>
                        {
                            ["address"] = host,
                            ["port"] = port,
                            ["users"] = new object[] { user }
                        }
                    }
                },
                ["streamSettings"] = BuildStreamSettings(
                    network,
                    security,
                    sni,
                    fp,
                    streamHost,
                    path,
                    headerType,
                    alpn,
                    publicKey,
                    shortId,
                    spiderX,
                    query["mode"])
            };

            var protocol = string.Equals(security, "reality", StringComparison.OrdinalIgnoreCase) ? "VLESS / REALITY" : "VLESS";
            return new ParsedShare
            {
                RawLink = raw,
                Protocol = protocol,
                Name = FragmentName(uri, protocol + " • " + host),
                Host = host,
                Port = port,
                Outbound = outbound
            };
        }

        private static ParsedShare ParseTrojan(string raw)
        {
            var uri = new Uri(raw);
            var password = Uri.UnescapeDataString(uri.UserInfo ?? string.Empty);
            var host = uri.Host;
            var port = uri.Port;
            if (string.IsNullOrWhiteSpace(password) || string.IsNullOrWhiteSpace(host) || port <= 0)
                throw new InvalidOperationException("Trojan فاقد password/host/port است.");

            var query = HttpUtility.ParseQueryString(uri.Query);
            var network = NormalizeNetwork(query["type"]);
            var security = string.IsNullOrWhiteSpace(query["security"]) ? "tls" : query["security"];
            var sni = First(query["sni"], query["peer"]);
            var streamHost = First(query["host"], query["authority"]);
            var path = First(query["path"], query["serviceName"]);

            var server = new Dictionary<string, object>
            {
                ["address"] = host,
                ["port"] = port,
                ["password"] = password
            };
            var outbound = new Dictionary<string, object>
            {
                ["protocol"] = "trojan",
                ["settings"] = new Dictionary<string, object> { ["servers"] = new object[] { server } },
                ["streamSettings"] = BuildStreamSettings(
                    network,
                    security,
                    sni,
                    query["fp"],
                    streamHost,
                    path,
                    query["headerType"],
                    query["alpn"],
                    First(query["pbk"], query["publicKey"]),
                    First(query["sid"], query["shortId"]),
                    First(query["spx"], query["spiderX"]),
                    query["mode"])
            };
            return new ParsedShare
            {
                RawLink = raw,
                Protocol = "TROJAN",
                Name = FragmentName(uri, "TROJAN • " + host),
                Host = host,
                Port = port,
                Outbound = outbound
            };
        }

        private static Dictionary<string, object> BuildStreamSettings(
            string network,
            string security,
            string sni,
            string fingerprint,
            string host,
            string path,
            string headerType,
            string alpn,
            string publicKey,
            string shortId,
            string spiderX,
            string mode)
        {
            var stream = new Dictionary<string, object>
            {
                ["network"] = network,
                ["security"] = string.IsNullOrWhiteSpace(security) || security == "none" ? "none" : security.ToLowerInvariant()
            };

            if (string.Equals(security, "tls", StringComparison.OrdinalIgnoreCase))
            {
                var tls = new Dictionary<string, object>
                {
                    ["serverName"] = First(sni, host),
                    ["allowInsecure"] = false
                };
                if (!string.IsNullOrWhiteSpace(fingerprint)) tls["fingerprint"] = fingerprint;
                var alpns = SplitCsv(alpn);
                if (alpns.Length > 0) tls["alpn"] = alpns;
                stream["tlsSettings"] = tls;
            }
            else if (string.Equals(security, "reality", StringComparison.OrdinalIgnoreCase))
            {
                var reality = new Dictionary<string, object>
                {
                    ["serverName"] = First(sni, host),
                    ["fingerprint"] = string.IsNullOrWhiteSpace(fingerprint) ? "chrome" : fingerprint,
                    ["publicKey"] = publicKey ?? string.Empty,
                    ["shortId"] = shortId ?? string.Empty,
                    ["spiderX"] = string.IsNullOrWhiteSpace(spiderX) ? "/" : spiderX
                };
                stream["realitySettings"] = reality;
            }

            switch (network)
            {
                case "ws":
                    var ws = new Dictionary<string, object> { ["path"] = string.IsNullOrWhiteSpace(path) ? "/" : path };
                    if (!string.IsNullOrWhiteSpace(host)) ws["headers"] = new Dictionary<string, object> { ["Host"] = host };
                    stream["wsSettings"] = ws;
                    break;
                case "grpc":
                    stream["grpcSettings"] = new Dictionary<string, object>
                    {
                        ["serviceName"] = path ?? string.Empty,
                        ["multiMode"] = string.Equals(mode, "multi", StringComparison.OrdinalIgnoreCase)
                    };
                    break;
                case "httpupgrade":
                    var upgrade = new Dictionary<string, object> { ["path"] = string.IsNullOrWhiteSpace(path) ? "/" : path };
                    if (!string.IsNullOrWhiteSpace(host)) upgrade["host"] = host;
                    stream["httpupgradeSettings"] = upgrade;
                    break;
                case "xhttp":
                case "splithttp":
                    stream["network"] = "xhttp";
                    var xhttp = new Dictionary<string, object> { ["path"] = string.IsNullOrWhiteSpace(path) ? "/" : path };
                    if (!string.IsNullOrWhiteSpace(host)) xhttp["host"] = host;
                    if (!string.IsNullOrWhiteSpace(mode)) xhttp["mode"] = mode;
                    stream["xhttpSettings"] = xhttp;
                    break;
                case "h2":
                case "http":
                    stream["network"] = "h2";
                    stream["httpSettings"] = new Dictionary<string, object>
                    {
                        ["path"] = string.IsNullOrWhiteSpace(path) ? "/" : path,
                        ["host"] = string.IsNullOrWhiteSpace(host) ? new object[0] : new object[] { host }
                    };
                    break;
                default:
                    stream["network"] = "tcp";
                    if (!string.IsNullOrWhiteSpace(headerType) && !string.Equals(headerType, "none", StringComparison.OrdinalIgnoreCase))
                    {
                        stream["tcpSettings"] = new Dictionary<string, object>
                        {
                            ["header"] = new Dictionary<string, object> { ["type"] = headerType }
                        };
                    }
                    break;
            }
            return stream;
        }

        private static void ReplaceEndpoint(Dictionary<string, object> outbound, string endpointIp)
        {
            var settings = D(outbound, "settings");
            var vnext = A(settings, "vnext");
            if (vnext != null && vnext.Length > 0)
            {
                var server = vnext[0] as Dictionary<string, object>;
                if (server != null) server["address"] = endpointIp;
                return;
            }
            var servers = A(settings, "servers");
            if (servers != null && servers.Length > 0)
            {
                var server = servers[0] as Dictionary<string, object>;
                if (server != null) server["address"] = endpointIp;
            }
        }

        private static Dictionary<string, object> DeepClone(Dictionary<string, object> source)
        {
            return Json.Deserialize<Dictionary<string, object>>(Json.Serialize(source));
        }

        private static string DecodeSubscriptionIfNeeded(string text)
        {
            if (text.IndexOf("://", StringComparison.Ordinal) >= 0) return text;
            try
            {
                var decoded = Encoding.UTF8.GetString(DecodeBase64Flexible(text));
                return decoded.IndexOf("://", StringComparison.Ordinal) >= 0 ? decoded : text;
            }
            catch { return text; }
        }

        private static byte[] DecodeBase64Flexible(string value)
        {
            var normalized = (value ?? string.Empty).Trim().Replace('-', '+').Replace('_', '/');
            normalized = new string(normalized.Where(c => !char.IsWhiteSpace(c)).ToArray());
            var pad = normalized.Length % 4;
            if (pad > 0) normalized = normalized.PadRight(normalized.Length + (4 - pad), '=');
            return Convert.FromBase64String(normalized);
        }

        private static string NormalizeNetwork(string network)
        {
            var value = (network ?? string.Empty).Trim().ToLowerInvariant();
            if (string.IsNullOrWhiteSpace(value) || value == "raw") return "tcp";
            return value;
        }

        private static string FragmentName(Uri uri, string fallback)
        {
            var fragment = uri.Fragment;
            if (string.IsNullOrWhiteSpace(fragment)) return fallback;
            return DecodeName(Uri.UnescapeDataString(fragment.TrimStart('#')), fallback);
        }

        private static string DecodeName(string name, string fallback)
        {
            if (string.IsNullOrWhiteSpace(name)) return fallback;
            try { return Uri.UnescapeDataString(name); } catch { return name; }
        }

        private static string First(params string[] values)
        {
            return values == null ? string.Empty : values.FirstOrDefault(v => !string.IsNullOrWhiteSpace(v)) ?? string.Empty;
        }

        private static object[] SplitCsv(string value)
        {
            if (string.IsNullOrWhiteSpace(value)) return new object[0];
            return value.Split(new[] { ',' }, StringSplitOptions.RemoveEmptyEntries).Select(v => (object)v.Trim()).Where(v => !string.IsNullOrWhiteSpace((string)v)).ToArray();
        }

        private static string S(Dictionary<string, object> map, string key)
        {
            if (map == null) return string.Empty;
            object value;
            return map.TryGetValue(key, out value) && value != null ? Convert.ToString(value, System.Globalization.CultureInfo.InvariantCulture) : string.Empty;
        }

        private static int I(Dictionary<string, object> map, string key, int fallback)
        {
            int parsed;
            return int.TryParse(S(map, key), out parsed) ? parsed : fallback;
        }

        private static Dictionary<string, object> D(Dictionary<string, object> map, string key)
        {
            if (map == null) return null;
            object value;
            return map.TryGetValue(key, out value) ? value as Dictionary<string, object> : null;
        }

        private static object[] A(Dictionary<string, object> map, string key)
        {
            if (map == null) return null;
            object value;
            return map.TryGetValue(key, out value) ? value as object[] : null;
        }
    }
}
