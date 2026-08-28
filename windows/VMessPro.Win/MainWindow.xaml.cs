using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;

namespace VMessPro.Win
{
    public partial class MainWindow : Window
    {
        private readonly ProfileStore _store = new ProfileStore();
        private readonly VpnEngine _engine = new VpnEngine();
        private readonly DispatcherTimer _clockTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        private readonly DispatcherTimer _trafficTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        private List<VpnProfile> _profiles;
        private VpnProfile _selected;
        private bool _busy;

        public MainWindow()
        {
            InitializeComponent();
            _profiles = _store.LoadProfiles();
            _engine.SnapshotChanged += Engine_SnapshotChanged;

            var selectedId = _store.LoadSelectedId();
            _selected = _profiles.FirstOrDefault(p => p.Id == selectedId) ?? _profiles.FirstOrDefault();
            BindProfiles();
            UpdateSelectedProfile();
            UpdateSnapshot(_engine.Snapshot);
            UpdateClock();

            _clockTimer.Tick += (s, e) => UpdateClock();
            _clockTimer.Start();
            _trafficTimer.Tick += (s, e) =>
            {
                var snapshot = _engine.PollTraffic();
                UpdateTraffic(snapshot);
            };
            _trafficTimer.Start();

            if (!_engine.CoreFilesPresent)
            {
                StatusText.Text = "Core ویندوز کامل نیست";
                StatusText.Foreground = Brush("#FF4F65");
                StatusDot.Fill = Brush("#FF4F65");
            }
        }

        private void Engine_SnapshotChanged(ConnectionSnapshot snapshot)
        {
            Dispatcher.BeginInvoke(new Action(() => UpdateSnapshot(snapshot)));
        }

        private void UpdateSnapshot(ConnectionSnapshot snapshot)
        {
            var connected = snapshot.State == WinVpnState.Connected;
            var connecting = snapshot.State == WinVpnState.Preparing || snapshot.State == WinVpnState.Connecting || snapshot.State == WinVpnState.Verifying;

            if (connected)
            {
                SetPowerColors("#10E3FF", "#3EE8A5", "#0879EE");
                PowerLabel.Text = "قطع اتصال";
                SecuritySubText.Text = "فعال";
                SecuritySubText.Foreground = Brush("#49F0AE");
                IpSubText.Text = "واقعی";
                IpSubText.Foreground = Brush("#49F0AE");
            }
            else if (connecting)
            {
                SetPowerColors("#FFC74A", "#FFD760", "#C67622");
                PowerLabel.Text = "در حال اتصال…";
                SecuritySubText.Text = "در حال بررسی";
                SecuritySubText.Foreground = Brush("#FFC74A");
                IpSubText.Text = "در انتظار";
                IpSubText.Foreground = Brush("#FFC74A");
            }
            else
            {
                SetPowerColors("#FF4F65", "#FF7481", "#8B1D58");
                PowerLabel.Text = "اتصال";
                SecuritySubText.Text = "آماده";
                SecuritySubText.Foreground = Brush("#C77BFF");
                IpSubText.Text = snapshot.State == WinVpnState.Error ? "خطا" : "قطع";
                IpSubText.Foreground = Brush("#FF4F65");
            }

            StatusText.Text = snapshot.Message ?? "—";
            var statusColor = connected ? "#78F456" : connecting ? "#FFC74A" : "#FF4F65";
            StatusText.Foreground = Brush(statusColor);
            StatusDot.Fill = Brush(statusColor);

            if (snapshot.LatencyMs.HasValue) PingText.Text = snapshot.LatencyMs.Value.ToString(CultureInfo.InvariantCulture);
            else if (_selected != null && _selected.LatencyMs.HasValue) PingText.Text = _selected.LatencyMs.Value.ToString(CultureInfo.InvariantCulture);
            else PingText.Text = "—";

            IpText.Text = ShortIp(snapshot.PublicIp);
            StatsIpText.Text = "IP: " + (string.IsNullOrWhiteSpace(snapshot.PublicIp) ? "—" : snapshot.PublicIp);
            StatsStateText.Text = "وضعیت: " + (snapshot.Message ?? "—");
            UpdateSelectedProfile();
            UpdateTraffic(snapshot);
        }

        private void UpdateTraffic(ConnectionSnapshot snapshot)
        {
            SpeedText.Text = snapshot.DownloadMbps.ToString("0.0", CultureInfo.InvariantCulture);
            StatsDownloadText.Text = snapshot.DownloadMbps.ToString("0.0", CultureInfo.InvariantCulture) + " Mbps";
            StatsUploadText.Text = snapshot.UploadMbps.ToString("0.0", CultureInfo.InvariantCulture) + " Mbps";
        }

        private void SetPowerColors(string outer, string center, string edge)
        {
            PowerOuter.BorderBrush = Brush(outer);
            PowerMid.BorderBrush = Brush("#EEFFFFFF");
            PowerCore.BorderBrush = Brush(outer);
            PowerCenterStop.Color = (Color)ColorConverter.ConvertFromString(center);
            PowerEdgeStop.Color = (Color)ColorConverter.ConvertFromString(edge);
            PowerGlow.Color = (Color)ColorConverter.ConvertFromString(outer);
        }

        private async void PowerButton_Click(object sender, RoutedEventArgs e)
        {
            if (_busy) return;
            if (_engine.State == WinVpnState.Connected)
            {
                _busy = true;
                PowerButton.IsEnabled = false;
                try { await _engine.DisconnectAsync(); }
                finally { _busy = false; PowerButton.IsEnabled = true; }
                return;
            }

            if (_selected == null)
            {
                ShowPage(ServersPage);
                MessageBox.Show(this, "ابتدا یک کانفیگ اضافه و انتخاب کنید.", "VMess Pro", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            _busy = true;
            PowerButton.IsEnabled = false;
            try
            {
                await _engine.ConnectAsync(_selected);
                _store.SaveProfiles(_profiles);
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, ex.Message, "اتصال ناموفق", MessageBoxButton.OK, MessageBoxImage.Error);
            }
            finally
            {
                _busy = false;
                PowerButton.IsEnabled = true;
                UpdateSelectedProfile();
                BindProfiles();
            }
        }

        private void ServerCard_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
        {
            ShowPage(ServersPage);
        }

        private void HomeNav_Click(object sender, RoutedEventArgs e) { ShowPage(HomePage); }
        private void ServersNav_Click(object sender, RoutedEventArgs e) { ShowPage(ServersPage); }
        private void StatsNav_Click(object sender, RoutedEventArgs e) { ShowPage(StatsPage); }
        private void SettingsNav_Click(object sender, RoutedEventArgs e) { ShowPage(SettingsPage); }

        private void ShowPage(UIElement page)
        {
            HomePage.Visibility = Visibility.Collapsed;
            ServersPage.Visibility = Visibility.Collapsed;
            StatsPage.Visibility = Visibility.Collapsed;
            SettingsPage.Visibility = Visibility.Collapsed;
            page.Visibility = Visibility.Visible;
        }

        private void AddButton_Click(object sender, RoutedEventArgs e)
        {
            ImportTextBox.Text = string.Empty;
            ImportOverlay.Visibility = Visibility.Visible;
            ImportTextBox.Focus();
        }

        private void ImportCancel_Click(object sender, RoutedEventArgs e)
        {
            ImportOverlay.Visibility = Visibility.Collapsed;
        }

        private async void ImportConfirm_Click(object sender, RoutedEventArgs e)
        {
            var text = ImportTextBox.Text;
            if (string.IsNullOrWhiteSpace(text)) return;
            try
            {
                var imported = await _engine.NormalizeImportAsync(text);
                if (imported.Count == 0)
                {
                    MessageBox.Show(this, "کانفیگ معتبری پیدا نشد.", "Import", MessageBoxButton.OK, MessageBoxImage.Warning);
                    return;
                }

                var existing = new Dictionary<string, VpnProfile>(_profiles.ToDictionary(p => p.Id, p => p), StringComparer.OrdinalIgnoreCase);
                foreach (var profile in imported)
                {
                    VpnProfile previous;
                    if (existing.TryGetValue(profile.Id, out previous))
                    {
                        profile.LatencyMs = previous.LatencyMs;
                        profile.LastSuccess = previous.LastSuccess;
                    }
                    existing[profile.Id] = profile;
                }
                _profiles = existing.Values.ToList();
                if (_selected == null) _selected = imported.First();
                _store.SaveProfiles(_profiles);
                if (_selected != null) _store.SaveSelectedId(_selected.Id);
                BindProfiles();
                UpdateSelectedProfile();
                ImportOverlay.Visibility = Visibility.Collapsed;
                ShowPage(ServersPage);
                MessageBox.Show(this, imported.Count + " کانفیگ ذخیره شد.", "VMess Pro", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, ex.Message, "Import ناموفق", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void ProfilesList_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            var profile = ProfilesList.SelectedItem as VpnProfile;
            if (profile == null) return;
            _selected = profile;
            _store.SaveSelectedId(profile.Id);
            UpdateSelectedProfile();
        }

        private async void TestAllButton_Click(object sender, RoutedEventArgs e)
        {
            await TestAllAsync(false);
        }

        private async void SmartButton_Click(object sender, RoutedEventArgs e)
        {
            await TestAllAsync(true);
        }

        private async Task TestAllAsync(bool selectBest)
        {
            if (_busy || _profiles.Count == 0) return;
            if (_engine.State == WinVpnState.Connected)
            {
                MessageBox.Show(this, "برای تست همه ابتدا VPN را قطع کنید.", "VMess Pro", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }
            _busy = true;
            try
            {
                var done = 0;
                foreach (var profile in _profiles)
                {
                    done++;
                    StatusText.Text = "تست واقعی " + done + " از " + _profiles.Count;
                    StatusText.Foreground = Brush("#FFC74A");
                    try { await _engine.TestProfileAsync(profile); }
                    catch { profile.LastSuccess = false; }
                    BindProfiles();
                }

                _profiles = _profiles
                    .OrderByDescending(p => p.LastSuccess)
                    .ThenBy(p => p.LatencyMs ?? int.MaxValue)
                    .ToList();
                if (selectBest)
                {
                    var best = _profiles.FirstOrDefault(p => p.LastSuccess && p.LatencyMs.HasValue);
                    if (best != null)
                    {
                        _selected = best;
                        _store.SaveSelectedId(best.Id);
                    }
                }
                _store.SaveProfiles(_profiles);
                BindProfiles();
                UpdateSelectedProfile();
                UpdateSnapshot(_engine.Snapshot);
            }
            finally { _busy = false; }
        }

        private void SplitButton_Click(object sender, RoutedEventArgs e)
        {
            MessageBox.Show(
                this,
                "در Build ویندوز ۸.۱ تونل فعلی Full-system است. Split برنامه‌به‌برنامه به Windows Filtering Platform نیاز دارد و عمداً به‌صورت نمایشی فعال نشده است.",
                "Split Tunnel",
                MessageBoxButton.OK,
                MessageBoxImage.Information);
        }

        private void BindProfiles()
        {
            var selectedId = _selected == null ? null : _selected.Id;
            ProfilesList.ItemsSource = null;
            ProfilesList.ItemsSource = _profiles;
            if (!string.IsNullOrWhiteSpace(selectedId))
                ProfilesList.SelectedItem = _profiles.FirstOrDefault(p => p.Id == selectedId);
        }

        private void UpdateSelectedProfile()
        {
            if (_selected == null)
            {
                ServerNameText.Text = "انتخاب سرور";
                ServerMetaText.Text = "برای اتصال یک پروفایل انتخاب کنید";
                SecurityText.Text = "Xray";
                StatsServerText.Text = "سرور: —";
                return;
            }
            ServerNameText.Text = string.IsNullOrWhiteSpace(_selected.Name) ? "Xray profile" : _selected.Name;
            var latency = _selected.LatencyMs.HasValue ? _selected.LatencyMs.Value + " ms" : "تست نشده";
            ServerMetaText.Text = (_selected.Protocol ?? "XRAY").ToUpperInvariant() + " • " + latency;
            SecurityText.Text = (_selected.Protocol ?? "Xray").ToUpperInvariant();
            PingText.Text = _selected.LatencyMs.HasValue ? _selected.LatencyMs.Value.ToString(CultureInfo.InvariantCulture) : "—";
            StatsServerText.Text = "سرور: " + ServerNameText.Text;
        }

        private void UpdateClock()
        {
            var now = DateTime.Now;
            ClockText.Text = ToPersianDigits(now.ToString("HH:mm:ss", CultureInfo.InvariantCulture));
            var pc = new PersianCalendar();
            var year = pc.GetYear(now);
            var month = pc.GetMonth(now);
            var day = pc.GetDayOfMonth(now);
            PersianDateText.Text = PersianWeekday(now.DayOfWeek) + " " + ToPersianDigits(day.ToString()) + " " + PersianMonth(month) + " " + ToPersianDigits(year.ToString());
        }

        private static string PersianWeekday(DayOfWeek day)
        {
            switch (day)
            {
                case DayOfWeek.Saturday: return "شنبه";
                case DayOfWeek.Sunday: return "یکشنبه";
                case DayOfWeek.Monday: return "دوشنبه";
                case DayOfWeek.Tuesday: return "سه‌شنبه";
                case DayOfWeek.Wednesday: return "چهارشنبه";
                case DayOfWeek.Thursday: return "پنجشنبه";
                default: return "جمعه";
            }
        }

        private static string PersianMonth(int month)
        {
            var names = new[] { "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند" };
            return month >= 1 && month <= 12 ? names[month] : string.Empty;
        }

        private static string ToPersianDigits(string value)
        {
            const string latin = "0123456789";
            const string persian = "۰۱۲۳۴۵۶۷۸۹";
            var result = value ?? string.Empty;
            for (var i = 0; i < latin.Length; i++) result = result.Replace(latin[i], persian[i]);
            return result;
        }

        private static string ShortIp(string ip)
        {
            if (string.IsNullOrWhiteSpace(ip)) return "—";
            return ip.Length <= 14 ? ip : ip.Substring(0, 11) + "…";
        }

        private static SolidColorBrush Brush(string hex)
        {
            return new SolidColorBrush((Color)ColorConverter.ConvertFromString(hex));
        }

        private void Window_Closing(object sender, System.ComponentModel.CancelEventArgs e)
        {
            _clockTimer.Stop();
            _trafficTimer.Stop();
            _engine.Dispose();
        }
    }
}
