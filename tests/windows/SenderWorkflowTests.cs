using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Authentication;
using System.Threading;
using System.Threading.Tasks;
using TvText;

internal static class SenderWorkflowTests
{
    internal static void Run(){Scenarios().GetAwaiter().GetResult();}
    private static async Task Scenarios()
    {
        string id=Guid.NewGuid().ToString();
        var saved=new Connection{DeviceId=id,Name="TV",Endpoint="127.0.0.1:45831",Token=new string('a',64),Fingerprint=new string('b',64)};
        var relocated=new DiscoveredTv{DeviceId=id,Name="TV",Endpoint="127.0.0.2:45831"};
        int sends=0,scans=0,writes=0;Exception failure=null;Reply response=new Reply{Code=0,DeviceId=id,Token=new string('c',64)};
        var workflow=new SenderWorkflow(
            delegate(string endpoint,string pin,int command,string credential,string requestId,string text,CancellationToken token)
            {token.ThrowIfCancellationRequested();if(command==3){Interlocked.Increment(ref sends);if(failure!=null)throw failure;}return response;},
            delegate(int milliseconds,CancellationToken token){scans++;token.ThrowIfCancellationRequested();return Task.FromResult(new List<DiscoveredTv>{relocated});},
            delegate(Connection connection,IEnumerable<DiscoveredTv> list,CancellationToken token){token.ThrowIfCancellationRequested();return Task.FromResult(relocated);},
            delegate(string endpoint,CancellationToken token){return saved.Fingerprint;},
            delegate(Connection value){writes++;throw new IOException("Fixture disk failure");});
        var sent=await workflow.SendAsync(saved,"draft",CancellationToken.None);
        Check(sent.Reply==response&&sends==1&&scans==0,"confirmed send performs no search or replay");
        failure=new IOException("Fixture offline");
        sent=await workflow.SendAsync(saved,"keep draft",CancellationToken.None);
        Check(sent.Error==failure&&sent.Recovery.Verified==relocated&&sends==2&&scans==1,"failed send finds one verified address without replay");
        Check(saved.Endpoint=="127.0.0.1:45831"&&writes==0,"recovery cannot mutate selection or persist before UI accepts it");
        failure=new AuthenticationException("Fixture changed certificate");
        sent=await workflow.SendAsync(saved,"private draft",CancellationToken.None);
        Check(sent.Error==failure&&sent.Recovery.Verified==relocated&&sends==3&&scans==2,"old-address certificate failure discovers the original TV without replay");
        failure=null;response=new Reply{Code=0,DeviceId=Guid.NewGuid().ToString(),Token=new string('c',64)};
        sent=await workflow.SendAsync(saved,"private draft",CancellationToken.None);
        Check(sent.Error is AuthenticationException&&sends==4&&scans==3,"wrong receiver identity is rejected and recovery never repeats the text");
        saved.Name="Local name";
        var pair=await workflow.PairAsync(relocated,saved.Endpoint,saved.Fingerprint,"12345678",new[]{saved},CancellationToken.None);
        Check(pair.Connection==null&&pair.ErrorKey=="changed_id","pairing cannot overwrite another TV");
        response=new Reply{Code=0,DeviceId=id,Token=new string('c',64)};
        pair=await workflow.PairAsync(relocated,saved.Endpoint,saved.Fingerprint,"12345678",new[]{saved},CancellationToken.None);
        Check(pair.Connection.Name=="Local name"&&pair.Connection.Token==response.Token&&writes==0,"pairing returns validated credentials for UI confirmation/persistence");
        Check(!workflow.TrySave(saved)&&writes==1,"write failure remains an explicit result");
        response.Code=1;Check(await workflow.RevokeAsync(saved,CancellationToken.None),"already revoked is a successful unpair notification");
        using(var cancellation=new CancellationTokenSource())
        {
            cancellation.Cancel();bool cancelled=false;
            try{await workflow.SendAsync(saved,"cancelled",cancellation.Token);}catch(OperationCanceledException){cancelled=true;}
            Check(cancelled&&sends==4&&scans==3,"cancelled work never reaches the sender or discovery");
        }
        Console.WriteLine("PASS: independent Windows workflow: validated pairing, no replay, verified recovery proposals, pin/identity rejection, explicit save errors and cancellation");
        int candidates=-1;
        var filtering=new SenderWorkflow(null,null,
            delegate(Connection connection,IEnumerable<DiscoveredTv> list,CancellationToken token){candidates=0;foreach(var tv in list)candidates++;return Task.FromResult<DiscoveredTv>(null);},null,null);
        var unchanged=new DiscoveredTv{DeviceId=saved.DeviceId,Endpoint=saved.Endpoint};
        await filtering.VerifyAsync(saved,new[]{unchanged},CancellationToken.None);
        Check(candidates==0,"normal send verification excludes the unchanged address");
        await filtering.VerifyAsync(saved,new[]{unchanged},CancellationToken.None,true);
        Check(candidates==1,"blocked pairing refresh includes the unchanged address for authenticated recovery");
    }
    private static void Check(bool value,string message){if(!value)throw new Exception(message);}
}
