using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;

namespace TvText
{
    internal static class ConnectionResolver
    {
        internal static async Task<DiscoveredTv> FindVerifiedAsync(Connection saved, IEnumerable<DiscoveredTv> candidates, CancellationToken cancellationToken = default(CancellationToken))
        {
            using (CancellationTokenSource budget = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken))
            {
                budget.CancelAfter(8000);
                foreach (DiscoveredTv tv in candidates)
                {
                    if (saved.DeviceId.Length == 0 || tv.DeviceId != saved.DeviceId) continue;
                    try
                    {
                        budget.Token.ThrowIfCancellationRequested();
                        Reply reply = await Task.Run(delegate { return Client.Exchange(tv.Endpoint, saved.Fingerprint, 2, saved.Token, "", "", budget.Token); });
                        if (reply.Code == 0 && reply.DeviceId == saved.DeviceId) return tv;
                    }
                    catch (OperationCanceledException) { cancellationToken.ThrowIfCancellationRequested(); return null; }
                    catch { }
                }
                return null;
            }
        }
    }


}
