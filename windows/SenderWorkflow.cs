using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Threading;
using System.Threading.Tasks;

namespace TvText
{
    // Network/persistence scenarios have no dependency on Windows Forms.
    internal sealed class SenderWorkflow
    {
        internal delegate Reply Exchange(string endpoint,string fingerprint,int command,string credential,string id,string text,CancellationToken token);
        private readonly Exchange exchange;
        private readonly Func<int,CancellationToken,Task<List<DiscoveredTv>>> discover;
        private readonly Func<Connection,IEnumerable<DiscoveredTv>,CancellationToken,Task<DiscoveredTv>> verify;
        private readonly Func<string,CancellationToken,string> probe;
        private readonly Action<Connection> save;

        internal SenderWorkflow():this(Client.Exchange,Discovery.FindAsync,ConnectionResolver.FindVerifiedAsync,Client.Probe,delegate(Connection value){value.Save();}){}
        internal SenderWorkflow(Exchange exchange,Func<int,CancellationToken,Task<List<DiscoveredTv>>> discover,
            Func<Connection,IEnumerable<DiscoveredTv>,CancellationToken,Task<DiscoveredTv>> verify,
            Func<string,CancellationToken,string> probe,Action<Connection> save)
        {this.exchange=exchange;this.discover=discover;this.verify=verify;this.probe=probe;this.save=save;}

        internal Task<List<DiscoveredTv>> DiscoverAsync(int milliseconds,CancellationToken token) {return discover(milliseconds,token);}
        internal Task<DiscoveredTv> VerifyAsync(Connection saved,IEnumerable<DiscoveredTv> list,CancellationToken token,bool includeCurrent=false)
        {
            if(includeCurrent)return verify(saved,list,token);
            var changed=new List<DiscoveredTv>();
            foreach(var tv in list)if(tv.Endpoint!=saved.Endpoint)changed.Add(tv);
            return verify(saved,changed,token);
        }
        internal Task<string> ProbeAsync(string endpoint,CancellationToken token)
        {return Task.Run(delegate{token.ThrowIfCancellationRequested();return probe(endpoint,token);},token);}
        internal bool TrySave(Connection value)
        {try{save(value);return true;}catch{return false;}}

        internal sealed class PairResult
        {
            internal Connection Connection;
            internal int Code;
            internal string ErrorKey;
        }
        internal async Task<PairResult> PairAsync(DiscoveredTv tv,string endpoint,string fingerprint,string code,IEnumerable<Connection> profiles,CancellationToken token)
        {
            Reply reply=await Task.Run(delegate{return exchange(endpoint,fingerprint,1,code,"","",token);},token);
            token.ThrowIfCancellationRequested();
            if(reply.Code!=0||reply.Token.Length!=64)return new PairResult{Code=reply.Code==0?6:reply.Code};
            Guid id;
            if(!Guid.TryParse(reply.DeviceId,out id))return new PairResult{ErrorKey="update_apk"};
            if(tv.DeviceId.Length>0&&tv.DeviceId!=id.ToString())return new PairResult{ErrorKey="changed_id"};
            string name=tv.Name;
            foreach(Connection profile in profiles)if(profile.DeviceId==id.ToString()){name=profile.Name;break;}
            return new PairResult{Connection=new Connection{Endpoint=endpoint,Fingerprint=fingerprint,Token=reply.Token,DeviceId=id.ToString(),Name=name}};
        }
        internal sealed class Recovery
        {
            internal List<DiscoveredTv> Devices;
            internal DiscoveredTv Verified;
        }
        internal async Task<Recovery> RecoverAsync(Connection saved,CancellationToken token)
        {
            var list=await discover(2500,token);
            token.ThrowIfCancellationRequested();
            var found=await verify(saved,list,token);
            token.ThrowIfCancellationRequested();
            // Return a proposal. The UI applies it only while this selection is current.
            return new Recovery{Devices=list,Verified=found};
        }
        internal sealed class SendResult
        {
            internal Reply Reply;
            internal Exception Error;
            internal Recovery Recovery;
        }
        internal async Task<SendResult> SendAsync(Connection saved,string text,CancellationToken token)
        {
            Exception failure;
            try
            {
                string id=Guid.NewGuid().ToString();
                Reply reply=await Task.Run(delegate{return exchange(saved.Endpoint,saved.Fingerprint,3,saved.Token,id,text,token);},token);
                token.ThrowIfCancellationRequested();Client.VerifyIdentity(reply,saved);
                return new SendResult{Reply=reply};
            }
            catch(OperationCanceledException){throw;}
            catch(Exception error)
            {
                failure=error;
            }
            var result=new SendResult{Error=failure};
            // The old DHCP address can now belong to another TLS device.
            // Recovery verifies the original certificate, ID and token and
            // only proposes an address; it never repeats the INSERT request.
            if(IsNetworkError(failure)||failure is AuthenticationException)
            {
                try{result.Recovery=await RecoverAsync(saved,token);}
                catch(OperationCanceledException){throw;}
                catch{ /* Preserve the original send error. Never replay its text. */ }
            }
            token.ThrowIfCancellationRequested();return result;
        }
        internal async Task<bool> RevokeAsync(Connection saved,CancellationToken token)
        {
            try
            {
                Reply reply=await Task.Run(delegate{return exchange(saved.Endpoint,saved.Fingerprint,4,saved.Token,"","",token);},token);
                token.ThrowIfCancellationRequested();Client.VerifyIdentity(reply,saved);
                return reply.Code==0||reply.Code==1;
            }
            catch(OperationCanceledException){throw;}
            catch{return false;}
        }
        private static bool IsNetworkError(Exception error)
        {return error is IOException||error is SocketException||error is TimeoutException;}
    }
}
