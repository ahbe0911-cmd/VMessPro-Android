using System;

namespace VMessPro.Win
{
    public enum WinVpnState
    {
        Disconnected,
        Preparing,
        Connecting,
        Verifying,
        Connected,
        Error
    }

    public sealed class VpnProfile
    {
        public string Id { get; set; }
        public string Name { get; set; }
        public string Protocol { get; set; }
        public string Host { get; set; }
        public string RawLink { get; set; }
        public int? LatencyMs { get; set; }
        public bool LastSuccess { get; set; }

        public override string ToString()
        {
            return string.IsNullOrWhiteSpace(Name) ? (Protocol ?? "Xray") : Name;
        }
    }

    public sealed class ConnectionSnapshot
    {
        public WinVpnState State { get; set; }
        public string Message { get; set; }
        public int? LatencyMs { get; set; }
        public string PublicIp { get; set; }
        public double DownloadMbps { get; set; }
        public double UploadMbps { get; set; }
        public DateTime ConnectedSince { get; set; }
    }
}
