using System;
using System.Drawing;
using System.Reflection;
using System.Windows.Forms;
using TvText;

internal static class DpiLayoutTests
{
    private static void Check(bool condition,string message){if(!condition)throw new Exception(message);}
    internal static int Run()
    {
        Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);
        PropertyInfo language=typeof(UiText).GetProperty("Current",BindingFlags.Static|BindingFlags.NonPublic);
        MethodInfo setLanguage=language.GetSetMethod(true);
        MethodInfo applyLanguage=typeof(MainForm).GetMethod("ApplyLanguage",BindingFlags.Instance|BindingFlags.NonPublic);
        string original=UiText.Current;
        float actualDpi=0;
        try
        {
            foreach(string current in new[]{"ru","en"})
            foreach(int count in new[]{0,1,3,9})
            {
                setLanguage.Invoke(null,new object[]{current});
                Connection[] profiles=new Connection[count];
                for(int i=0;i<count;i++)profiles[i]=new Connection{DeviceId=Guid.NewGuid().ToString(),Name="Test TV "+i,Token="preview-only"};
                using(MainForm form=new MainForm(count==0?new Connection():profiles[0],true,profiles))
                {
                    form.StartPosition=FormStartPosition.Manual;form.Location=new Point(-20000,-20000);form.ShowInTaskbar=false;
                    form.Show();Application.DoEvents();
                    actualDpi=form.CurrentAutoScaleDimensions.Height;
                    float scale=actualDpi/96f;
                    TableLayoutPanel root=(TableLayoutPanel)form.Controls.Find("MainLayout",true)[0];
                    Panel viewport=(Panel)form.Controls.Find("MainViewport",true)[0];
                    Control send=form.Controls.Find("SendButton",true)[0];
                    Control languageButton=form.Controls.Find("LanguageButton",true)[0];
                    TvDeviceList list=(TvDeviceList)form.Controls.Find("DeviceList",true)[0];
                    ThreeLineTextBox input=(ThreeLineTextBox)form.Controls.Find("TextBuffer",true)[0];
                    Check(Math.Abs(root.MinimumSize.Height-664*scale)<=1,"content minimum height must scale exactly once at startup");
                    Check(Math.Abs(root.Padding.Left-28*scale)<=1,"outer padding must scale exactly once at startup");
                    int[] heights=root.GetRowHeights();
                    Check(Math.Abs(heights[0]-78*scale)<=1&&Math.Abs(heights[1]-286*scale)<=1,"header and device rows must scale exactly once at startup");
                    Check(Math.Abs(send.Height-53*scale)<=2,"send button keeps its intended height at native DPI");
                    Check(list.ClientSize.Height==list.ItemHeight*3,"native DPI preserves exactly three TV rows");
                    Check(input.ClientSize.Height>=input.Font.Height*3,"native DPI preserves three complete input lines");
                    CheckViewport(viewport,send,languageButton);
                    Rectangle initialSend=send.Bounds;
                    for(int i=0;i<5;i++)
                    {
                        // Exercise relocalization without writing the user's language preference.
                        setLanguage.Invoke(null,new object[]{UiText.Current=="ru"?"en":"ru"});
                        applyLanguage.Invoke(form,null);
                        form.PerformLayout();Application.DoEvents();
                    }
                    Check(send.Bounds==initialSend,"language changes must not enlarge the layout");
                    form.ClientSize=new Size(form.ClientSize.Width+120,form.ClientSize.Height+80);
                    form.PerformLayout();Application.DoEvents();
                    CheckViewport(viewport,send,languageButton);
                    form.Close();
                }
            }
        }
        finally{setLanguage.Invoke(null,new object[]{original});}
        Console.WriteLine("PASS: native startup DPI "+actualDpi+": RU/EN, 0/1/3/9 TVs, single-pass table metrics, visible Send/language buttons, no page scrollbar at default size");
        return 0;
    }
    private static void CheckViewport(Panel viewport,Control send,Control languageButton)
    {
        Check(!viewport.VerticalScroll.Visible&&!viewport.HorizontalScroll.Visible,"default window must fit without scrolling the page");
        foreach(Control control in new[]{send,languageButton})
        {
            Rectangle bounds=viewport.RectangleToClient(control.Parent.RectangleToScreen(control.Bounds));
            Check(viewport.ClientRectangle.Contains(bounds),control.Name+" must be fully visible inside the viewport");
        }
    }
}
