using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Windows.Forms;
using TvText;

internal static class DeviceListTests
{
    private static void Check(bool condition,string message){if(!condition)throw new Exception(message);}
    internal static int Run(string output)
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Directory.CreateDirectory(output);
        RenameStorage(output);
        MethodInfo languageSetter=typeof(UiText).GetProperty("Current",BindingFlags.Static|BindingFlags.NonPublic).GetSetMethod(true);
        string originalLanguage=UiText.Current;
        try
        {
            foreach(string language in new[]{"ru","en"})
            {
                languageSetter.Invoke(null,new object[]{language});
                Check(UiText.T("device_needs_pairing")== (language=="ru"?"Нажмите на кнопку «Подключить»":"Click the “Connect” button"),"unpaired TV row uses the direct Connect hint");
                foreach(int count in new[]{0,1,2,3,9})
                foreach(float scale in new[]{1f,1.25f,1.5f,2f})
                {
                    List<Connection> profiles=new List<Connection>();
                    for(int i=0;i<count;i++)profiles.Add(new Connection{DeviceId=Guid.NewGuid().ToString(),Endpoint="127.0.0.1:45832",Name=i==0?"Гостиная · Google TV Android 14":i==1?"Спальня · Xiaomi TV Android 12":"Телевизор с очень длинным названием · Android 14 · "+i,Token="test-only"});
                    using(MainForm form=new MainForm(count>0?profiles[0]:new Connection(),true,profiles))
                    {
                        form.StartPosition=FormStartPosition.Manual;form.Location=new Point(-20000,-20000);form.ShowInTaskbar=false;
                        form.Show();Application.DoEvents();
                        if(scale!=1f){form.Scale(new SizeF(scale,scale));form.PerformLayout();Application.DoEvents();}
                        TvDeviceList list=(TvDeviceList)form.Controls.Find("DeviceList",true)[0];
                        Control connect=form.Controls.Find("ConnectButton",true)[0];
                        ThreeLineTextBox input=(ThreeLineTextBox)form.Controls.Find("TextBuffer",true)[0];
                        Check(list.Visible&&list.Items.Count==count,"list shows every TV without a popup");
                        Check(list.AccessibilityObject.Role==AccessibleRole.List,"native list accessibility role");
                        Check(input.Height==input.ThreeLineHeight&&input.Height>input.Font.Height*3&&input.ScrollBars==ScrollBars.Vertical,"text input reserves native edit padding while staying exactly three lines high");
                        Check(list.ClientSize.Height==list.ItemHeight*3,"list reserves exactly three TV rows even when empty");
                        Check(!HasVerticalScrollbar(input),"empty input has no visible scrollbar");
                        input.Text="one\r\ntwo\r\nthree";Check(!HasVerticalScrollbar(input),"three input lines do not show a scrollbar");
                        input.Text="one\r\ntwo\r\nthree\r\nfour\r\nfive";input.SelectionStart=input.TextLength;input.ScrollToCaret();
                        Check(HasVerticalScrollbar(input),"input scrollbar appears only after overflow");
                        Check(input.GetPositionFromCharIndex(0).Y<0,"text after the third line scrolls vertically");
                        Check(input.Height==input.ThreeLineHeight,"long text does not grow the input");input.Clear();
                        Check(!HasVerticalScrollbar(input),"input scrollbar disappears after clearing");
                        input.Text=new string('Ж',500);Check(HasVerticalScrollbar(input),"soft-wrapped text also enables scrolling");input.Clear();
                        Check(HasVerticalScrollbar(list)==(count>3),"TV list scrollbar is visible only for more than three devices");
                        Check(TextRenderer.MeasureText(connect.Text,connect.Font).Width<=connect.Width,"localized connect button fits");
                        Control renameButton=form.Controls.Find("RenameButton",true)[0];
                        Check(renameButton.Text==UiText.T("rename")&&TextRenderer.MeasureText(renameButton.Text,renameButton.Font).Width<=renameButton.Width,"localized text rename button fits");
                        if(count==0)Check(list.SelectedIndex==-1&&!connect.Enabled,"empty list has no fake device or enabled connect action");
                        if(count>0)
                        {
                            Check(list.GetItemRectangle(0).Bottom<=list.ClientSize.Height,"first TV row is fully visible");
                            MethodInfo key=typeof(Control).GetMethod("OnKeyDown",BindingFlags.Instance|BindingFlags.NonPublic);
                            key.Invoke(list,new object[]{new KeyEventArgs(Keys.End)});
                            Check(list.SelectedIndex==count-1,"End selects the last TV");
                            Check(list.GetItemRectangle(count-1).Bottom<=list.ClientSize.Height+1,"keyboard selection scrolls the last TV into view");
                            if(count>3)Check(list.TopIndex>0,"long list scrolls instead of growing the window");
                            key.Invoke(list,new object[]{new KeyEventArgs(Keys.Home)});
                            if(count>=2&&scale==1f)HoverChecks(list);
                        }
                        CheckBounds(form);
                        if(language=="ru"&&scale==1f)
                        using(Bitmap bitmap=new Bitmap(form.Width,form.Height))
                        {form.DrawToBitmap(bitmap,new Rectangle(Point.Empty,bitmap.Size));bitmap.Save(Path.Combine(output,"devices-"+count+".png"));}
                        if(count==9&&scale==1f)
                        {
                            form.Size=form.MinimumSize;form.PerformLayout();Application.DoEvents();
                            CheckBounds(form);
                            Check(input.Height==input.ThreeLineHeight,"minimum window size keeps all three input lines usable");
                        }
                        form.Close();
                    }
                }
                Console.WriteLine("PASS: open TV list "+language+": empty/1/2/3/9 devices, long names, scrolling, native list accessibility, minimum size and 100–200% layout scaling");
                RenameDialogs(output,language);
            }
        }
        finally{languageSetter.Invoke(null,new object[]{originalLanguage});}
        return 0;
    }
    private static void HoverChecks(TvDeviceList list)
    {
        MethodInfo move=typeof(Control).GetMethod("OnMouseMove",BindingFlags.Instance|BindingFlags.NonPublic);
        object selected=list.SelectedItem;int changes=0;
        EventHandler changed=delegate{changes++;};list.SelectedIndexChanged+=changed;
        for(int i=0;i<80;i++)
        {
            move.Invoke(list,new object[]{new MouseEventArgs(MouseButtons.None,0,12,(i%2)*list.ItemHeight+list.ItemHeight/2,0)});
            list.Update();
        }
        list.SelectedIndexChanged-=changed;
        Check(changes==0&&Object.ReferenceEquals(list.SelectedItem,selected),"repainting hovered rows never changes selection");
        int redraws=0;InvalidateEventHandler invalidated=delegate{redraws++;};list.Invalidated+=invalidated;
        for(int i=0;i<80;i++)move.Invoke(list,new object[]{new MouseEventArgs(MouseButtons.None,0,12,list.ItemHeight+list.ItemHeight/2,0)});
        list.Invalidated-=invalidated;
        Check(redraws==0,"moving within the same hovered row does not trigger a repaint loop");
    }
    private static bool HasVerticalScrollbar(Control control){return (GetWindowLong(control.Handle,-16)&0x00200000)!=0;}
    [DllImport("user32.dll",EntryPoint="GetWindowLongW")]private static extern int GetWindowLong(IntPtr window,int index);
    private static void RenameStorage(string output)
    {
        string previous=DeviceNames.StoragePathOverride;
        string directory=Path.Combine(output,"names-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(directory);
        DeviceNames.StoragePathOverride=Path.Combine(directory,"device-names.dat");
        try
        {
            string first=Guid.NewGuid().ToString(),second=Guid.NewGuid().ToString();
            DeviceNames.Set(first,"  Гостиная 😀  ");DeviceNames.Set(second,"Спальня");
            System.Threading.Tasks.Task[] writers=new System.Threading.Tasks.Task[8];
            for(int i=0;i<writers.Length;i++)
            {string id=Guid.NewGuid().ToString();writers[i]=System.Threading.Tasks.Task.Run(delegate{DeviceNames.Set(id,"Test TV");});}
            System.Threading.Tasks.Task.WaitAll(writers);
            Dictionary<string,string> saved=DeviceNames.Load();
            Check(saved.Count==10&&saved[first]=="Гостиная 😀"&&saved[second]=="Спальня","independent aliases persist with Unicode and concurrent writes");
            Check(!DeviceNames.ValidName("")&&!DeviceNames.ValidName("   ")&&!DeviceNames.ValidName("one\ntwo")&&!DeviceNames.ValidName(new string('x',81))&&!DeviceNames.ValidName("\ud800"),"empty, multiline, oversized and broken Unicode names are rejected");
            Connection profile=new Connection{DeviceId=first,Endpoint="127.0.0.2:45832",Name="Google TV Android 14",Token="key-kept",Fingerprint="pin-kept"};
            using(MainForm form=new MainForm(profile,true,new[]{profile},saved))
            {
                form.StartPosition=FormStartPosition.Manual;form.Location=new Point(-20000,-20000);form.ShowInTaskbar=false;form.Show();Application.DoEvents();
                TvDeviceList list=(TvDeviceList)form.Controls.Find("DeviceList",true)[0];
                TextBox input=(TextBox)form.Controls.Find("TextBuffer",true)[0];input.Text="Сохранить черновик";
                Check(list.SelectedItem.ToString()=="Гостиная 😀","alias is restored by device ID after an IP change");
                MethodInfo rename=typeof(MainForm).GetMethod("RenameDevice",BindingFlags.Instance|BindingFlags.NonPublic);
                rename.Invoke(form,new object[]{first,"Телевизор в гостиной"});
                Check(list.SelectedItem.ToString()=="Телевизор в гостиной"&&((DiscoveredTv)list.SelectedItem).Name==profile.Name,"local rename leaves the advertised name unchanged");
                Check(profile.Endpoint=="127.0.0.2:45832"&&profile.Token=="key-kept"&&profile.Fingerprint=="pin-kept"&&input.Text=="Сохранить черновик","renaming preserves endpoint, pairing credentials and draft");
                MethodInfo rebuild=typeof(MainForm).GetMethod("RebuildDevices",BindingFlags.Instance|BindingFlags.NonPublic);
                rebuild.Invoke(form,new object[]{null});Check(list.SelectedItem.ToString()=="Телевизор в гостиной","discovery refresh preserves alias");
                FieldInfo discovered=typeof(MainForm).GetField("discovered",BindingFlags.Instance|BindingFlags.NonPublic);
                discovered.SetValue(form,new List<DiscoveredTv>{new DiscoveredTv{DeviceId=second,Endpoint="127.0.0.3:45832",Name="New TV"}});rebuild.Invoke(form,new object[]{null});list.SelectedIndex=1;
                rename.Invoke(form,new object[]{second,"Кухня"});
                Check(list.SelectedItem.ToString()=="Кухня"&&!form.Controls.Find("SendButton",true)[0].Enabled,"an unpaired TV can be renamed without granting sending access");
                form.Close();
            }
            byte[] corrupt={1,2,3};File.WriteAllBytes(DeviceNames.StoragePathOverride,corrupt);
            bool failed=false;try{DeviceNames.Set(first,"Нельзя перезаписать");}catch{failed=true;}
            Check(failed&&BitConverter.ToString(File.ReadAllBytes(DeviceNames.StoragePathOverride))==BitConverter.ToString(corrupt),"damaged alias storage is preserved rather than overwritten");
            Console.WriteLine("PASS: local TV names: Unicode, independent IDs, concurrent atomic writes, IP change, preserved pairing/draft, unpaired devices and corrupt-file protection");
        }
        finally{DeviceNames.StoragePathOverride=previous;}
    }
    private static void RenameDialogs(string output,string language)
    {
        foreach(float scale in new[]{1f,1.25f,1.5f,2f})
        using(RenameDialog dialog=new RenameDialog("Гостиная"))
        {
            dialog.StartPosition=FormStartPosition.Manual;dialog.Location=new Point(-20000,-20000);dialog.Show();Application.DoEvents();
            if(scale!=1f){dialog.Scale(new SizeF(scale,scale));dialog.PerformLayout();Application.DoEvents();}
            TextBox name=(TextBox)dialog.Controls.Find("DeviceNameInput",true)[0];
            Button save=(Button)dialog.Controls.Find("SaveNameButton",true)[0];
            MethodInfo enter=typeof(Form).GetMethod("ProcessDialogKey",BindingFlags.Instance|BindingFlags.NonPublic);
            name.Text="  ";Check(!save.Enabled,"empty local name cannot be submitted");enter.Invoke(dialog,new object[]{Keys.Enter});
            Check(dialog.DialogResult==DialogResult.None,"Enter does not submit an empty name");
            name.Text="Гостиная 😀";Check(save.Enabled,"Unicode local name is accepted");CheckBounds(dialog);
            if(scale==1f)
            using(Bitmap bitmap=new Bitmap(dialog.Width,dialog.Height))
            {dialog.DrawToBitmap(bitmap,new Rectangle(Point.Empty,bitmap.Size));bitmap.Save(Path.Combine(output,"rename-"+language+".png"));}
            enter.Invoke(dialog,new object[]{Keys.Enter});
            Check(dialog.DialogResult==DialogResult.OK&&dialog.DeviceName=="Гостиная 😀","Enter submits the renamed TV");
            dialog.Close();
        }
        Console.WriteLine("PASS: rename dialog "+language+": validation, Enter and 100–200% layout scaling");
    }
    private static void CheckBounds(Control parent)
    {
        foreach(Control child in parent.Controls)
        {
            ScrollableControl scroll=parent as ScrollableControl;
            bool scrolling=scroll!=null&&scroll.AutoScroll;
            Check(scrolling||child.Left>=0&&child.Top>=0&&child.Right<=parent.ClientSize.Width+1&&child.Bottom<=parent.ClientSize.Height+1,"no clipped "+child.Name+" in "+parent.GetType().Name);
            CheckBounds(child);
        }
    }
}
