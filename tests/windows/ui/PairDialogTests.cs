using System;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Windows.Forms;
using TvText;

internal static class PairDialogTests
{
    private static void Check(bool condition,string message)
    {
        if(!condition)throw new Exception(message);
    }
    internal static int Run(string output)
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Directory.CreateDirectory(output);
        MethodInfo languageSetter=typeof(UiText).GetProperty("Current",BindingFlags.Static|BindingFlags.NonPublic).GetSetMethod(true);
        string originalLanguage=UiText.Current;
        try
        {
            foreach(string language in new[]{"ru","en"})
            {
                languageSetter.Invoke(null,new object[]{language});
                foreach(float scale in new[]{1f,1.25f,1.5f,2f})
                using(PairDialog dialog=new PairDialog())
                {
                    dialog.StartPosition=FormStartPosition.Manual;
                    dialog.Location=new Point(-20000,-20000);
                    dialog.Show();Application.DoEvents();
                    if(scale!=1f){dialog.Scale(new SizeF(scale,scale));dialog.PerformLayout();Application.DoEvents();}
                    TextBox code=(TextBox)dialog.Controls.Find("PairCode",true)[0];
                    Control field=dialog.Controls.Find("PairCodeField",true)[0];
                    Button connect=(Button)dialog.Controls.Find("PairConnectButton",true)[0];
                    Label heading=(Label)dialog.Controls.Find("PairHeading",true)[0];
                    Label instructions=(Label)dialog.Controls.Find("PairInstructions",true)[0];
                    Check(!code.Multiline&&code.AutoSize,"native single-line input uses its preferred height");
                    Check(code.Height==code.PreferredHeight,"code and caret are not stretched vertically");
                    Check(Math.Abs(code.Top-(field.ClientSize.Height-code.Height)/2)<=1,"code input is vertically centered");
                    Check(code.Left>=8&&code.Right<=field.Width-8&&code.Top>=3&&code.Bottom<=field.Height-3,"input stays clear of the rounded frame");
                    Rectangle fieldBounds=dialog.RectangleToClient(field.RectangleToScreen(field.ClientRectangle));
                    Rectangle buttonBounds=dialog.RectangleToClient(connect.RectangleToScreen(connect.ClientRectangle));
                    Check(buttonBounds.Top-fieldBounds.Bottom>=16*scale,"visible gap between code field and button");
                    Check(fieldBounds.Left==buttonBounds.Left&&fieldBounds.Right==buttonBounds.Right,"input and button share both edges");
                    Check(TextRenderer.MeasureText(heading.Text,heading.Font).Width<=heading.Width,"localized title fits on one line");
                    Check(TextRenderer.MeasureText(instructions.Text,instructions.Font,new Size(instructions.Width,int.MaxValue),TextFormatFlags.WordBreak).Height<=instructions.Height,"localized instructions fit");
                    CheckChildren(dialog);
                    MethodInfo enter=typeof(Form).GetMethod("ProcessDialogKey",BindingFlags.Instance|BindingFlags.NonPublic);
                    code.Text="1234567";enter.Invoke(dialog,new object[]{Keys.Enter});
                    Check(dialog.DialogResult==DialogResult.None,"incomplete code is not accepted");
                    code.Text="1234abcd";enter.Invoke(dialog,new object[]{Keys.Enter});
                    Check(code.Text=="1234","non-numeric characters are filtered from the code field");
                    Check(dialog.DialogResult==DialogResult.None,"non-numeric code is not accepted");
                    code.Text="12345678";
                    if(scale==1f)
                    using(Bitmap bitmap=new Bitmap(dialog.Width,dialog.Height))
                    {
                        dialog.DrawToBitmap(bitmap,new Rectangle(Point.Empty,bitmap.Size));
                        bitmap.Save(Path.Combine(output,"pair-"+language+".png"));
                    }
                    enter.Invoke(dialog,new object[]{Keys.Enter});
                    Check(dialog.DialogResult==DialogResult.OK&&dialog.Code=="12345678","Enter submits a valid code");
                    Console.WriteLine("PASS: pairing dialog "+language+" "+(scale*100)+"%: layout, text, caret height, validation, Enter");
                    dialog.Close();
                }
            }
        }
        finally{languageSetter.Invoke(null,new object[]{originalLanguage});}
        return 0;
    }
    private static void CheckChildren(Control parent)
    {
        foreach(Control child in parent.Controls)
        {
            Check(child.Left>=0&&child.Top>=0&&child.Right<=parent.ClientSize.Width+1&&child.Bottom<=parent.ClientSize.Height+1,"no clipped "+child.GetType().Name+" in "+parent.GetType().Name);
            CheckChildren(child);
        }
    }
}
