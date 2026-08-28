using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Web.Script.Serialization;

namespace VMessPro.Win
{
    public sealed class ProfileStore
    {
        private readonly string _root;
        private readonly string _profilesPath;
        private readonly string _settingsPath;
        private readonly JavaScriptSerializer _json = new JavaScriptSerializer { MaxJsonLength = 16 * 1024 * 1024 };

        public ProfileStore()
        {
            _root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VMessPro");
            Directory.CreateDirectory(_root);
            _profilesPath = Path.Combine(_root, "profiles.json");
            _settingsPath = Path.Combine(_root, "settings.json");
        }

        public string RootDirectory { get { return _root; } }

        public List<VpnProfile> LoadProfiles()
        {
            try
            {
                if (!File.Exists(_profilesPath)) return new List<VpnProfile>();
                var text = File.ReadAllText(_profilesPath, Encoding.UTF8);
                return _json.Deserialize<List<VpnProfile>>(text) ?? new List<VpnProfile>();
            }
            catch
            {
                return new List<VpnProfile>();
            }
        }

        public void SaveProfiles(IEnumerable<VpnProfile> profiles)
        {
            var list = profiles.Where(p => p != null && !string.IsNullOrWhiteSpace(p.RawLink)).ToList();
            File.WriteAllText(_profilesPath, _json.Serialize(list), new UTF8Encoding(false));
        }

        public string LoadSelectedId()
        {
            try
            {
                if (!File.Exists(_settingsPath)) return null;
                var map = _json.Deserialize<Dictionary<string, string>>(File.ReadAllText(_settingsPath, Encoding.UTF8));
                string value;
                return map != null && map.TryGetValue("selectedId", out value) ? value : null;
            }
            catch
            {
                return null;
            }
        }

        public void SaveSelectedId(string id)
        {
            var map = new Dictionary<string, string> { { "selectedId", id ?? string.Empty } };
            File.WriteAllText(_settingsPath, _json.Serialize(map), new UTF8Encoding(false));
        }

        public static string StableId(string raw)
        {
            using (var sha = SHA256.Create())
            {
                var bytes = sha.ComputeHash(Encoding.UTF8.GetBytes((raw ?? string.Empty).Trim()));
                var sb = new StringBuilder(bytes.Length * 2);
                foreach (var b in bytes) sb.Append(b.ToString("x2"));
                return sb.ToString();
            }
        }
    }
}
