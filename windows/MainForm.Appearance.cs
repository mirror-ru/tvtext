using System;
using System.Drawing;
using System.Windows.Forms;

namespace TvText
{
    public sealed partial class MainForm
    {
        private void ApplyLanguage()
        {
            Text = UiText.T("app_title");
            versionLabel.Text = UiText.T("version", System.Reflection.Assembly.GetExecutingAssembly().GetName().Version.ToString(3));
            deviceCaption.Text = UiText.T("device_caption");
            devices.AccessibleName = UiText.T("device_list_accessible");
            refresh.AccessibleName = UiText.T("refresh_accessible");
            tips.SetToolTip(refresh, UiText.T("refresh_tip"));
            UpdateThemeButton();
            rename.Text = UiText.T("rename"); rename.AccessibleName = UiText.T("rename_tv"); tips.SetToolTip(rename, UiText.T("rename_tip"));
            textPrompt.Text = UiText.T("text_prompt");
            input.AccessibleName = UiText.T("input_accessible");
            send.Text = UiText.T("send");
            footerHint.Text = UiText.T("footer_hint");
            languageButton.Text = UiText.ButtonText;
            deviceMessages.Clear(); devices.EmptyMessage = String.Empty; devices.EmptyMessageError = false;
            devices.Invalidate();
            UpdateControls();
        }
        private void ApplyTheme()
        {
            SuspendLayout();
            ApplyThemeControl(this);
            versionLabel.ForeColor = Theme.Muted; deviceCaption.ForeColor = Theme.Muted; status.ForeColor = Theme.Muted; footerHint.ForeColor = Theme.Muted;
            input.ForeColor = Theme.Ink; input.BackColor = Theme.Surface;
            UpdateThemeButton(); Theme.ApplyTitleBar(this);
            ResumeLayout(true); Invalidate(true);
        }
        private void UpdateThemeButton()
        {
            string action = UiText.T(Theme.IsDark ? "theme_light" : "theme_dark");
            themeButton.AccessibleName = action; themeButton.AccessibleDescription = UiText.T(Theme.IsDark ? "theme_current_dark" : "theme_current_light");
            tips.SetToolTip(themeButton, action); themeButton.Invalidate();
        }
        private static void ApplyThemeControl(Control control)
        {
            if (control is Form) control.BackColor = Theme.Canvas;
            else if (control is Card) control.BackColor = Color.Transparent;
            else if (control is TvDeviceList) control.BackColor = Theme.Surface;
            else if (control is ThreeLineTextBox) control.BackColor = Theme.Surface;
            else if (control is TextBox) control.BackColor = Theme.Input;
            else if (control.Name == "MainViewport" || control.Name == "MainLayout" || control.Name == "Header" || control.Name == "Footer" || control.Name == "FooterActions") control.BackColor = Theme.Canvas;
            else if (control is Panel || control is TableLayoutPanel || control is FlowLayoutPanel) control.BackColor = Theme.Surface;
            if (control is Label || control is Form || control is TextBox || control is TvDeviceList) control.ForeColor = Theme.Ink;
            if (control is TextBox || control is TvDeviceList) Theme.ApplyNativeControl(control);
            control.Invalidate();
            foreach (Control child in control.Controls) ApplyThemeControl(child);
        }
    }
}
