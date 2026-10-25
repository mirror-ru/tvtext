using System;
using System.Collections.Generic;
using TvText;

internal static class ConnectionCatalogTests
{
    internal static void Run()
    {
        Connection paired = new Connection { DeviceId = Guid.NewGuid().ToString(), Name = "Saved TV", Token = "saved-token", Endpoint = "127.0.0.1:45831" };
        DiscoveredTv removed = new DiscoveredTv { DeviceId = Guid.NewGuid().ToString(), Name = "Removed", Endpoint = "127.0.0.2:45831" };
        Dictionary<string, string> names = new Dictionary<string, string> { { paired.DeviceId, "Living room" } };
        List<Connection> profiles = new List<Connection> { paired };
        List<object> rows = ConnectionCatalog.Build(profiles, new[] { removed }, removed, removed.DeviceId, names);
        Check(rows.Count == 1 && ((DiscoveredTv)rows[0]).LocalName == "Living room", "removed TV cannot return through discovery or fallback");
        Check(ConnectionCatalog.FindProfile(profiles, (DiscoveredTv)rows[0]) == paired, "selection keeps the saved credentials");
        Check(ConnectionCatalog.SelectionIndex(rows, removed.DeviceId) == 0, "remaining TV is selected immediately");
        Check(ConnectionCatalog.FindProfile(profiles, removed).Token.Length == 0, "unknown TV cannot inherit another TV's credentials");
        rows = ConnectionCatalog.Build(profiles, new DiscoveredTv[0], removed, "", names);
        Check(rows.Count == 2 && ConnectionCatalog.SelectionIndex(rows, removed.DeviceId) == 1, "lost advertisement keeps the chosen recipient");
        Check(ConnectionCatalog.SelectionIndex(new List<object>(), paired.DeviceId) == -1, "empty list clears selection");
        Console.WriteLine("PASS: Windows catalog recipient stability, local names, removal suppression and credential isolation");
    }

    private static void Check(bool condition, string message)
    {
        if (!condition) throw new Exception(message);
    }
}
