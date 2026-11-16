using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace TvText
{
    public sealed partial class MainForm
    {
        private DiscoveredTv Selected { get { return devices.SelectedItem as DiscoveredTv; } }
        private bool ActiveSelection()
        {
            DiscoveredTv selected = Selected; return connection.Token.Length > 0 && selected != null && (connection.DeviceId.Length > 0 ? selected.DeviceId == connection.DeviceId : selected.Endpoint == connection.Endpoint);
        }
        private bool IsPairedSelection()
        { return ActiveSelection() && !pairingRequired.Contains(connection.DeviceId); }
        private void SyncSelectedConnection(bool saveSelection)
        {
            connection = ConnectionCatalog.FindProfile(profiles, Selected);
            if (saveSelection && !preview && connection.Token.Length > 0)
            {
                try { connection.SaveSelection(); }
                catch { unsavedConnections.Add(connection.DeviceId); devices.Invalidate(); }
            }
        }
        private bool SaveConnection(Connection saved)
        {
            bool success = workflow.TrySave(saved);
            if (success) unsavedConnections.Remove(saved.DeviceId); else unsavedConnections.Add(saved.DeviceId);
            devices.Invalidate(); return success;
        }
        private bool ApplyEndpoint(Connection saved, string endpoint)
        {
            saved.Endpoint = endpoint;
            return SaveConnection(saved);
        }
        private void UpdateControls()
        {
            if (IsDisposed || closing) return;
            bool paired = IsPairedSelection();
            send.Enabled = !busy && paired && input.Text.Length > 0;
            connect.Enabled = !busy && Selected != null; connect.Text = paired ? UiText.T("unbind") : UiText.T("connect"); connect.Danger = false; connect.Primary = !paired;
            refresh.Enabled = !scanning && !busy; devices.Enabled = !busy; languageButton.Enabled = !busy; themeButton.Enabled = !busy;
            rename.Enabled = !busy && Selected != null && Selected.DeviceId.Length > 0;
        }
        private void ShowMessage(string message, bool error)
        {
            if (IsDisposed || closing) return;
            // The common status line stays empty. A message belongs to the
            // selected TV row; with no rows it is rendered inside the list.
            status.Text = String.Empty; status.ForeColor = Theme.Muted;
            DiscoveredTv selected = Selected;
            if (selected != null)
            {
                deviceMessages[selected.DeviceId] = message;
                devices.EmptyMessage = String.Empty; devices.EmptyMessageError = false;
            }
            else
            {
                deviceMessages.Clear(); devices.EmptyMessage = message; devices.EmptyMessageError = error;
            }
            devices.Invalidate();
        }
        private void ClearStatus()
        {
            if (IsDisposed || closing) return;
            status.Text = String.Empty; status.ForeColor = Theme.Muted;
            deviceMessages.Clear(); devices.EmptyMessage = String.Empty; devices.EmptyMessageError = false; devices.Invalidate();
        }
        private async Task UnpairAsync()
        {
            if (busy || closing || !IsPairedSelection()) return;
            if (MessageBox.Show(this, UiText.T("confirm_unbind"), UiText.T("unbind_title"), MessageBoxButtons.YesNo, MessageBoxIcon.Question, MessageBoxDefaultButton.Button1) != DialogResult.Yes) return;
            Connection saved = connection; string removedId = saved.DeviceId; bool remoteNotified = false; bool refreshAfterUnpair = false;
            busy = true; UpdateControls(); ShowMessage(UiText.T("unpairing"), false);
            try
            {
                try
                {
                    remoteNotified = await workflow.RevokeAsync(saved, shutdown.Token);
                    if (closing) return;
                }
                catch (Exception) { if (closing) return; }
                try { RemoveProfile(removedId); }
                catch (Exception e) { MessageBox.Show(this, e.Message, UiText.T("unbind_failed"), MessageBoxButtons.OK, MessageBoxIcon.Error); return; }
                refreshAfterUnpair = true;
                if (!remoteNotified)
                    MessageBox.Show(this, UiText.T("unbind_remote_failed"), UiText.T("unbind_remote_failed_title"), MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
            finally
            {
                busy = false; UpdateControls();
            }
            if (refreshAfterUnpair && !closing) await ScanAsync(false);
        }
        private void RemoveProfile(string deviceId)
        {
            if (!preview) Connection.Forget(deviceId);
            profiles.RemoveAll(delegate (Connection profile) { return profile.DeviceId == deviceId; });
            pairingRequired.Remove(deviceId); unsavedConnections.Remove(deviceId); suppressedDeviceId = deviceId;
            RebuildDevices(null); ClearStatus();
        }
        private void RenameSelected()
        {
            DiscoveredTv tv = Selected;
            if (busy || closing || tv == null) return;
            using (RenameDialog dialog = new RenameDialog(tv.ToString()))
            {
                if (dialog.ShowDialog(this) != DialogResult.OK) return;
                try { RenameDevice(tv.DeviceId, dialog.DeviceName); ClearStatus(); }
                catch (Exception error) { MessageBox.Show(this, error.Message, UiText.T("rename_failed"), MessageBoxButtons.OK, MessageBoxIcon.Error); }
            }
        }
        private void RenameDevice(string deviceId, string name)
        {
            Guid id;
            if (!Guid.TryParse(deviceId, out id) || !DeviceNames.ValidName(name)) throw new ArgumentException(UiText.T("name_required"));
            if (preview) localNames[id.ToString()] = name.Trim();
            else localNames = DeviceNames.Set(id.ToString(), name);
            RebuildDevices(null);
        }
        private void RebuildDevices(string preferredId)
        {
            DiscoveredTv previous = Selected;
            string desiredId = !String.IsNullOrEmpty(preferredId) ? preferredId : previous != null ? previous.DeviceId : connection.DeviceId;
            List<object> next = ConnectionCatalog.Build(profiles, discovered, previous, suppressedDeviceId, localNames);
            bool changed = next.Count != devices.Items.Count;
            for (int i = 0; !changed && i < next.Count; i++)
            {
                DiscoveredTv a = next[i] as DiscoveredTv, b = devices.Items[i] as DiscoveredTv;
                changed = a != null && b != null ? a.DeviceId != b.DeviceId || a.Name != b.Name || a.LocalName != b.LocalName || a.Endpoint != b.Endpoint : !Object.Equals(next[i], devices.Items[i]);
            }
            rebuilding = true;
            int target = ConnectionCatalog.SelectionIndex(next, desiredId);
            try { if (changed) devices.ReplaceItems(next, target); else devices.SelectedIndex = target; }
            finally { rebuilding = false; }
            if ((previous == null ? String.Empty : previous.DeviceId) != (Selected == null ? String.Empty : Selected.DeviceId)) ResetSelectionWork();
            SyncSelectedConnection(false);
            UpdateControls();
        }
        private async Task ScanAsync(bool userInitiated)
        {
            if (scanning || busy || closing) return; scanning = true; refreshAnimationStart = refreshClock.ElapsedTicks; refreshAngle = 0; refresh.SpinAngle = 0; refresh.Spinning = true; refreshAnimation.Start(); ClearStatus(); UpdateControls();
            int revision = selectionRevision;
            try
            {
                List<DiscoveredTv> list = await workflow.DiscoverAsync(5000, shutdown.Token); if (closing) return; discovered = list; suppressedDeviceId = String.Empty;
                if (busy) return;
                Connection saved = connection; CancellationToken token = selectionWork.Token; int peerRevision = selectionRevision;
                DiscoveredTv relocated = saved.Token.Length > 0 && ActiveSelection() ? await workflow.VerifyAsync(saved, list, token, pairingRequired.Contains(saved.DeviceId)) : null;
                if (closing) return;
                if (relocated != null && !busy && peerRevision == selectionRevision && ActiveSelection() && Object.ReferenceEquals(saved, connection))
                { ApplyEndpoint(saved, relocated.Endpoint); pairingRequired.Remove(saved.DeviceId); ClearStatus(); }
                RebuildDevices(null);
                if (Connection.LastReadError.Length == 0 && !busy && revision == selectionRevision)
                {
                    ClearStatus();
                }
            }
            catch (OperationCanceledException) { }
            catch (Exception e) { if (!busy && revision == selectionRevision && (userInitiated || connection.Token.Length == 0)) ShowMessage(UiText.T("discovery_failed_detail", e.GetBaseException().Message), true); }
            finally { scanning = false; refreshAnimation.Stop(); refresh.Spinning = false; refresh.SpinAngle = 0; refresh.Invalidate(); UpdateControls(); }
        }
        private async Task PairAsync()
        {
            if (busy || closing) return; DiscoveredTv tv = Selected; if (tv == null) { await ScanAsync(true); return; }
            busy = true; ResetSelectionWork(); UpdateControls();
            try
            {
                string host = tv.Endpoint, pairingCode;
                using (PairDialog dialog = new PairDialog())
                { if (dialog.ShowDialog(this) != DialogResult.OK) return; pairingCode = dialog.Code; }
                foreach (DiscoveredTv found in discovered) if (found.DeviceId == tv.DeviceId) { host = found.Endpoint; break; }
                CancellationToken token = shutdown.Token;
                ShowMessage(UiText.T("pairing"), false); string fingerprint = await workflow.ProbeAsync(host, token); if (closing) return;
                if (fingerprint != connection.Fingerprint)
                {
                    DialogResult answer = MessageBox.Show(this, UiText.T("fingerprint_question", Client.ShortFingerprint(fingerprint)), UiText.T("fingerprint_title"), MessageBoxButtons.YesNo, MessageBoxIcon.Question, MessageBoxDefaultButton.Button1);
                    if (answer != DialogResult.Yes) { ClearStatus(); return; }
                }
                SenderWorkflow.PairResult paired = await workflow.PairAsync(tv, host, fingerprint, pairingCode, profiles, token); if (closing) return;
                if (paired.Connection == null) { ShowMessage(paired.ErrorKey != null ? UiText.T(paired.ErrorKey) : UiText.ResponseMessage(paired.Code), true); return; }
                connection = paired.Connection;
                if (suppressedDeviceId == connection.DeviceId) suppressedDeviceId = String.Empty;
                pairingRequired.Remove(connection.DeviceId);
                int profileIndex = profiles.FindIndex(delegate (Connection profile) { return profile.DeviceId == connection.DeviceId; });
                if (profileIndex >= 0) profiles[profileIndex] = connection; else profiles.Add(connection);
                bool saved = SaveConnection(connection);
                RebuildDevices(connection.DeviceId); if (!saved) ShowMessage(UiText.T("success_save_failed"), true); else ClearStatus(); input.Focus();
            }
            catch (OperationCanceledException) { }
            catch (Exception e) { ShowMessage(ErrorText(e, false), true); }
            finally { busy = false; UpdateControls(); }
        }
        private async Task SendAsync()
        {
            if (busy || closing || !ActiveSelection() || pairingRequired.Contains(connection.DeviceId) || input.Text.Length == 0) return;
            string text = input.Text;
            if (Encoding.UTF8.GetByteCount(text) > Client.MaxText) { ShowMessage(UiText.T("too_long"), true); return; }
            Connection saved = connection; int revision = selectionRevision; busy = true; UpdateControls();
            try
            {
                // A refresh may have found a new address while another TV was
                // selected. Authenticate that candidate before the first send.
                DiscoveredTv relocated = await workflow.VerifyAsync(saved, new List<DiscoveredTv>(discovered), shutdown.Token);
                if (closing || revision != selectionRevision || !Object.ReferenceEquals(saved, connection)) return;
                if (relocated != null) ApplyEndpoint(saved, relocated.Endpoint);
                SenderWorkflow.SendResult result = await workflow.SendAsync(saved, text, shutdown.Token);
                if (closing || revision != selectionRevision || !Object.ReferenceEquals(saved, connection)) return;
                if (result.Error == null)
                {
                    Reply reply = result.Reply;
                    if (reply.Code == 1 && pairingRequired.Add(saved.DeviceId)) devices.Invalidate();
                    if (reply.Code == 0) ClearStatus(); else ShowMessage(UiText.ResponseMessage(reply.Code), true);
                    if (reply.Code == 0 && input.Text == text) input.Clear(); input.Focus();
                }
                else
                {
                    if (result.Error is AuthenticationException && result.Recovery != null && result.Recovery.Verified != null)
                        pairingRequired.Remove(saved.DeviceId);
                    else if (result.Error is AuthenticationException && pairingRequired.Add(saved.DeviceId)) devices.Invalidate();
                    string message = ErrorText(result.Error, true);
                    if (result.Recovery != null)
                    {
                        discovered = result.Recovery.Devices; suppressedDeviceId = String.Empty;
                        if (result.Recovery.Verified != null)
                        {
                            bool persisted = ApplyEndpoint(saved, result.Recovery.Verified.Endpoint);
                            message = UiText.T(persisted ? "endpoint_updated" : "endpoint_updated_save_failed");
                        }
                        RebuildDevices(saved.DeviceId);
                    }
                    ShowMessage(message, true);
                }
            }
            catch (OperationCanceledException) { }
            finally { busy = false; UpdateControls(); }
        }
        private static string ErrorText(Exception error, bool sending)
        {
            NetworkFailure network = error as NetworkFailure;
            if (network != null)
            {
                if (network.Stage == NetworkStage.Connect) return UiText.T("network_connect_failed");
                if (network.Stage == NetworkStage.Tls) return UiText.T("network_tls_failed");
                if (network.Stage == NetworkStage.Response) return sending ? UiText.T("send_no_confirmation") : UiText.T("network_response_failed");
            }
            if (error is ProtocolResponseFailure) return ((Exception)error).Message;
            if (error is AuthenticationException) return UiText.T("auth_error");
            if (error is EncoderFallbackException) return UiText.T("bad_character");
            if (sending && (error is IOException || error is TimeoutException || error is SocketException)) return UiText.T("send_no_confirmation");
            if (error is SocketException || error is IOException || error is TimeoutException) return UiText.T("tv_unavailable");
            return error.Message;
        }

    }
}
