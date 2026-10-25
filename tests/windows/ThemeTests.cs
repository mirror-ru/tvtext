using System;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Windows.Forms;
using TvText;

internal static class ThemeTests
{
    private static void Check(bool condition,string message){if(!condition)throw new Exception(message);}
    internal static int Run(string output)
    {
        Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);
        Directory.CreateDirectory(output);
        string previousPath=Theme.StoragePathOverride,previousLanguage=UiText.Current;
        MethodInfo setLanguage=typeof(UiText).GetProperty("Current",BindingFlags.Static|BindingFlags.NonPublic).GetSetMethod(true);
        Theme.StoragePathOverride=Path.Combine(output,"theme-"+Guid.NewGuid().ToString("N")+".txt");
        try
        {
            Theme.Reload();Check(!Theme.IsDark,"missing preference defaults to day mode");
            Theme.Toggle();Theme.Reload();Check(Theme.IsDark,"night mode persists after reload");
            Theme.Toggle();Theme.Reload();Check(!Theme.IsDark,"day mode persists after reload");
            File.WriteAllText(Theme.StoragePathOverride,"invalid");Theme.Reload();Check(!Theme.IsDark,"invalid preference falls back to day mode");
            foreach(string language in new[]{"ru","en"})
            foreach(float scale in new[]{1f,1.25f,1.5f,2f})
            {
                setLanguage.Invoke(null,new object[]{language});
                Connection[] profiles=new Connection[4];
                for(int i=0;i<profiles.Length;i++)profiles[i]=new Connection{DeviceId=Guid.NewGuid().ToString(),Name="Телевизор "+i,Token="preview-only"};
                using(MainForm form=new MainForm(profiles[0],true,profiles))
                {
                    Show(form,scale);
                    TvDeviceList list=(TvDeviceList)form.Controls.Find("DeviceList",true)[0];
                    TextBox input=(TextBox)form.Controls.Find("TextBuffer",true)[0];
                    Button theme=(Button)form.Controls.Find("ThemeButton",true)[0];
                    list.SelectedIndex=1;input.Text="Черновик · draft";input.Select(2,4);
                    Rectangle initialInput=input.Bounds,initialList=list.Bounds;
                    for(int i=0;i<4;i++)
                    {
                        theme.PerformClick();Application.DoEvents();
                        Check(Theme.IsDark==(i%2==0),"theme button alternates day and night");
                        Check(theme.Focused,"theme button retains keyboard focus");
                        Check(input.Text=="Черновик · draft"&&input.SelectionStart==2&&input.SelectionLength==4,"theme changes preserve the draft and selection");
                        Check(list.SelectedIndex==1&&list.Items.Count==4,"theme changes preserve the TV selection");
                        Check(input.Bounds==initialInput&&list.Bounds==initialList,"theme changes do not resize controls");
                        Check(form.BackColor==Theme.Canvas&&form.ForeColor==Theme.Ink,"window colors update");
                        Check(input.BackColor==Theme.Surface&&input.ForeColor==Theme.Ink,"native editor colors update and blend with the text card");
                        Check(list.BackColor==Theme.Surface&&list.ForeColor==Theme.Ink,"native TV list colors update");
                        Check(theme.AccessibleName==UiText.T(Theme.IsDark?"theme_light":"theme_dark"),"theme action is localized and announces the next mode");
                        Check(Contrast(Color.White,Theme.PrimaryFill)>=4.5&&Contrast(Color.White,Theme.PrimaryHover)>=4.5,"primary button text stays readable in both states");
                        Check(Contrast(Theme.ButtonInk,Theme.Button)>=4.5&&Contrast(Theme.ButtonInk,Theme.ButtonHover)>=4.5,"secondary buttons remain readable on hover in both themes");
                        Check(Contrast(Theme.Ink,Theme.Input)>=7&&Contrast(Theme.Muted,Theme.Surface)>=3,"body and secondary text remain readable");
                        CheckFooter(form);
                        Control card=form.Controls.Find("DeviceCard",true)[0];
                        using(Bitmap image=new Bitmap(card.Width,card.Height))
                        {
                            card.DrawToBitmap(image,new Rectangle(Point.Empty,image.Size));
                            Check(image.GetPixel(0,0).ToArgb()==Theme.Canvas.ToArgb(),"rounded card corners match the outer canvas");
                        }
                        if(i<2)
                        {
                            string mode=Theme.IsDark?"dark":"light";
                            if(scale==1f)Snapshot(form,Path.Combine(output,"main-"+language+"-"+mode+".png"));
                            Dialogs(output,language,mode,scale);
                        }
                    }
                    form.ClientSize=new Size(form.MinimumSize.Width-16,form.ClientSize.Height);Application.DoEvents();CheckFooter(form);
                    form.Close();
                }
            }
            Console.WriteLine("PASS: day/night persistence, live switching, contrast, rounded corners, draft/selection/focus preservation, RU/EN and 100–200% main/pair/rename layouts");
            return 0;
        }
        finally{Theme.StoragePathOverride=previousPath;Theme.Reload();setLanguage.Invoke(null,new object[]{previousLanguage});}
    }
    private static void Dialogs(string output,string language,string mode,float scale)
    {
        using(RenameDialog rename=new RenameDialog("Гостиная"))
        {
            Show(rename,scale);
            TextBox name=(TextBox)rename.Controls.Find("DeviceNameInput",true)[0];
            Control field=rename.Controls.Find("DeviceNameField",true)[0],hint=rename.Controls.Find("RenameHint",true)[0],heading=rename.Controls.Find("RenameHeading",true)[0];
            Button save=(Button)rename.Controls.Find("SaveNameButton",true)[0];
            Check(name.Focused&&name.SelectionLength==name.Text.Length,"rename starts with the existing name selected");
            Check(name.BackColor==Theme.Input&&name.ForeColor==Theme.Ink,"rename editor uses the current theme");
            Check(Math.Abs(name.Top-(field.Height-name.Height)/2)<=1&&name.Height==name.PreferredHeight,"rename caret keeps native height and vertical centering");
            Check(!hint.Text.EndsWith(".")&&!heading.Text.EndsWith("."),"rename text has no trailing full stops");
            Check(TextRenderer.MeasureText(hint.Text,hint.Font).Width<=hint.Width,"rename helper fits on one line");
            Rectangle fieldBounds=rename.RectangleToClient(field.RectangleToScreen(field.ClientRectangle));
            Rectangle saveBounds=rename.RectangleToClient(save.RectangleToScreen(save.ClientRectangle));
            Check(saveBounds.Top-fieldBounds.Bottom>=20*scale,"rename field and actions have a clear gap");
            Check(Math.Abs(saveBounds.Right-fieldBounds.Right)<=1,"rename Save aligns with the field edge");
            using(Bitmap image=new Bitmap(field.Width,field.Height))
            {
                field.DrawToBitmap(image,new Rectangle(Point.Empty,image.Size));
                Check(image.GetPixel(0,0).ToArgb()==Theme.Surface.ToArgb(),"rounded input corners match the surrounding card");
                Check(image.GetPixel(field.Width/2,5).ToArgb()==Theme.Input.ToArgb(),"rounded field interior uses the input fill");
            }
            CheckBounds(rename);
            if(scale==1f)Snapshot(rename,Path.Combine(output,"rename-"+language+"-"+mode+".png"));
            name.Text="   ";Check(!save.Enabled,"empty renamed TV cannot be saved");
            name.Text="Кухня";
            typeof(Form).GetMethod("ProcessDialogKey",BindingFlags.Instance|BindingFlags.NonPublic).Invoke(rename,new object[]{Keys.Escape});
            Check(rename.DialogResult==DialogResult.Cancel,"Escape cancels renaming");rename.Close();
        }
        using(PairDialog pair=new PairDialog())
        {
            Show(pair,scale);
            TextBox code=(TextBox)pair.Controls.Find("PairCode",true)[0];
            Check(code.BackColor==Theme.Input&&code.ForeColor==Theme.Ink,"new pairing dialog inherits the selected theme");
            CheckBounds(pair);
            if(scale==1f)Snapshot(pair,Path.Combine(output,"pair-"+language+"-"+mode+".png"));pair.Close();
        }
    }
    private static void Show(Form form,float scale)
    {
        form.StartPosition=FormStartPosition.Manual;form.Location=new Point(-20000,-20000);form.ShowInTaskbar=false;form.Show();Application.DoEvents();
        if(scale!=1f){form.Scale(new SizeF(scale,scale));form.PerformLayout();Application.DoEvents();}
    }
    private static void CheckFooter(MainForm form)
    {
        Control footer=form.Controls.Find("Footer",true)[0],hint=form.Controls.Find("FooterHint",true)[0],theme=form.Controls.Find("ThemeButton",true)[0],language=form.Controls.Find("LanguageButton",true)[0];
        Check(Math.Abs(hint.Left+hint.Width/2-footer.Width/2)<=1,"footer hint remains horizontally centered");
        Check(TextRenderer.MeasureText(hint.Text,hint.Font).Width<=hint.Width,"footer hint fits beside both settings buttons");
        Check(theme.Width>=theme.Height&&theme.Height>=20*theme.DeviceDpi/96f,"theme icon has enough space: "+theme.Size+" DPI "+theme.DeviceDpi);
        Check(language.Parent==theme.Parent&&language.Right<=theme.Left,"theme button sits beside RU/EN without overlap");
        CheckBounds(footer);
    }
    private static void CheckBounds(Control control)
    {
        foreach(Control child in control.Controls)
        {
            Check(child.Left>=0&&child.Top>=0&&child.Right<=control.ClientSize.Width+1&&child.Bottom<=control.ClientSize.Height+1,"no clipped control in "+control.Name);
            CheckBounds(child);
        }
    }
    private static void Snapshot(Form form,string path)
    {using(Bitmap image=new Bitmap(form.Width,form.Height)){form.DrawToBitmap(image,new Rectangle(Point.Empty,image.Size));image.Save(path);}}
    private static double Contrast(Color first,Color second)
    {double a=Luminance(first),b=Luminance(second);return (Math.Max(a,b)+.05)/(Math.Min(a,b)+.05);}
    private static double Luminance(Color color)
    {return .2126*Linear(color.R)+.7152*Linear(color.G)+.0722*Linear(color.B);}
    private static double Linear(byte component)
    {double value=component/255.0;return value<=.04045?value/12.92:Math.Pow((value+.055)/1.055,2.4);}
}
