using System;
using System.Collections.Generic;

namespace TvText
{
    // Builds the device list without depending on WinForms or changing credentials.
    internal static class ConnectionCatalog
    {
        internal static Connection FindProfile(List<Connection> profiles, DiscoveredTv selected)
        {
            if (selected == null) return new Connection();
            return profiles.Find(delegate(Connection profile) { return profile.DeviceId == selected.DeviceId; })
                ?? new Connection();
        }

        internal static DiscoveredTv Present(DiscoveredTv device, IDictionary<string, string> localNames)
        {
            string alias;
            localNames.TryGetValue(device.DeviceId, out alias);
            return new DiscoveredTv
            {
                Name = device.Name,
                DeviceId = device.DeviceId,
                Endpoint = device.Endpoint,
                LocalName = alias
            };
        }

        internal static List<object> Build(List<Connection> profiles, IEnumerable<DiscoveredTv> discovered,
            DiscoveredTv previous, string suppressedId, IDictionary<string, string> localNames)
        {
            List<object> devices = new List<object>();
            HashSet<string> listed = new HashSet<string>();
            foreach (Connection profile in profiles)
            {
                devices.Add(Present(new DiscoveredTv
                {
                    Name = profile.Name,
                    Endpoint = profile.Endpoint,
                    DeviceId = profile.DeviceId
                }, localNames));
                listed.Add(profile.DeviceId);
            }
            foreach (DiscoveredTv device in discovered)
            {
                if (device.DeviceId != suppressedId && listed.Add(device.DeviceId))
                    devices.Add(Present(device, localNames));
            }
            // Losing an advertisement must not silently change the recipient.
            // An explicitly removed device must not return through this fallback.
            if (previous != null && previous.DeviceId != suppressedId && listed.Add(previous.DeviceId))
                devices.Add(Present(previous, localNames));
            return devices;
        }

        internal static int SelectionIndex(List<object> devices, string desiredId)
        {
            int index = devices.FindIndex(delegate(object item) { return ((DiscoveredTv)item).DeviceId == desiredId; });
            return index >= 0 ? index : devices.Count > 0 ? 0 : -1;
        }
    }
}
