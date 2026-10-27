using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;

namespace TvText
{
    internal static class UiText
    {
        private static readonly Dictionary<string,string> ru = new Dictionary<string,string>
        {
            {"app_title", "TVText"},
            {"connection_changed", "Подключение изменено в другом окне. Перезапустите TVText."},
            {"version", "Версия {0}"},
            {"device_caption", "ТЕЛЕВИЗОРЫ"},
            {"device_list_accessible", "Список телевизоров"},
            {"device_needs_pairing", "Нажмите на кнопку «Подключить»"},
            {"rename_tv", "Переименовать телевизор"},
            {"rename", "Переименовать"},
            {"rename_tip", "Переименовать на этом устройстве"},
            {"rename_heading", "Имя телевизора"},
            {"rename_hint", "Имя будет видно только на этом устройстве"},
            {"rename_failed", "Не удалось сохранить имя"},
            {"name_required", "Введите имя от 1 до 80 символов в одну строку."},
            {"invalid_names", "Файл локальных имён повреждён. Исходный файл не изменён."},
            {"too_many_names", "Сохранено слишком много локальных имён телевизоров."},
            {"save", "Сохранить"},
            {"cancel", "Отмена"},
            {"connect", "Подключить"},
            {"paired", "Сопряжён"},
            {"unbind", "Отвязать"},
            {"confirm_unbind", "Отвязать этот телевизор от устройства?"},
            {"unbind_title", "Отвязать телевизор"},
            {"unbind_failed", "Не удалось отвязать телевизор"},
            {"unpairing", "Уведомляем телевизор об отвязке…"},
            {"unbind_remote_failed_title", "Не удалось уведомить телевизор"},
            {"unbind_remote_failed", "Подключение удалено с этого устройства, но уведомление об отвязке не передано на телевизор.\n\nУдалите это подключение в списке подключённых устройств на ТВ."},
            {"refresh_accessible", "Найти телевизоры"},
            {"refresh_tip", "Обновить список телевизоров"},
            {"text_prompt", "Что отправим на телевизор?"},
            {"input_accessible", "Текст для телевизора"},
            {"send", "Отправить на ТВ"},
            {"footer_hint", "Enter — отправить · Shift+Enter — новая строка"},
            {"theme_dark", "Включить ночной режим"},
            {"theme_light", "Включить дневной режим"},
            {"theme_current_dark", "Ночной режим включён"},
            {"theme_current_light", "Дневной режим включён"},
            {"televisions", "Телевизоры"},
            {"connected_tvs", "Подключённые телевизоры"},
            {"saved_connections", "Сохранённые подключения"},
            {"no_saved_tvs", "Сохранённых телевизоров пока нет."},
            {"delete", "Удалить"},
            {"close", "Закрыть"},
            {"confirm_delete", "Удалить подключение к телевизору «{0}»?"},
            {"delete_title", "Удаление подключения"},
            {"delete_failed", "Не удалось удалить подключение"},
            {"pairing", "Подключаемся к телевизору…"},
            {"fingerprint_question", "На экране ТВ указан такой же отпечаток?\n\n{0}\n\nСравните все четыре группы символов."},
            {"fingerprint_title", "Это ваш телевизор?"},
            {"update_apk", "Обновите APK на ТВ: не получен идентификатор устройства."},
            {"changed_id", "Изменился идентификатор ТВ. Обновите список и повторите подключение."},
            {"success_save_failed", "Готово, но ключ не удалось сохранить на диск."},
            {"save_failed", "Не удалось сохранить подключение. Оно доступно до закрытия приложения."},
            {"endpoint_updated_save_failed", "Адрес обновлён только до закрытия приложения. Проверьте текст на ТВ перед повтором."},
            {"too_long", "Слишком длинный текст. Максимум — 32 КБ UTF-8."},
            {"pair_first", "Сначала подключите устройство по коду."},
            {"bad_request", "Некорректный запрос TVText."},
            {"text_too_long_protocol", "Текст слишком длинный: предел — 32 КБ UTF-8."},
            {"bad_reply_size", "Неверный размер ответа."},
            {"invalid_storage", "Сохранённые подключения повреждены или недоступны. Исходный файл не изменён."},
            {"invalid_data", "Некорректные данные подключения."},
            {"need_pairing", "Нужно новое сопряжение. Нажмите «Подключить»."},
            {"pair_closed", "Срок действия кода истёк. Получите новый код в TVText на телевизоре."},
            {"bad_pin", "Неверный код подключения. Проверьте код на телевизоре."},
            {"no_field", "Откройте клавиатуру в приложении на ТВ и повторите отправку."},
            {"receiver_disabled", "Включите TVText в специальных возможностях телевизора."},
            {"auth_error", "Не удалось проверить ТВ. Нажмите «Подключить» для нового сопряжения."},
            {"bad_character", "В тексте есть повреждённый символ. Вставьте текст заново."},
            {"send_no_confirmation", "Нет подтверждения. Проверьте текст на ТВ перед повтором."},
            {"tv_unavailable", "ТВ недоступен. Нажмите «Обновить» и проверьте приём на ТВ."},
            {"endpoint_updated", "Соединение с ТВ восстановлено. Проверьте текст на ТВ перед повтором."},
            {"pair_dialog_title", "Подключить телевизор"},
            {"pair_dialog_heading", "Подключим ваш ТВ"},
            {"pair_dialog_hint", "На телевизоре откройте TVText и выберите\nподключение нового устройства."},
            {"pair_code", "Код с экрана ТВ · 8 цифр"},
            {"pair_code_accessible", "8 цифр кода подключения"},
            {"transport_bad_endpoint", "Некорректный адрес телевизора."},
            {"transport_bad_endpoint_refresh", "Некорректный адрес телевизора. Обновите список устройств."},
            {"transport_no_response", "Телевизор не отвечает. Проверьте Wi-Fi и включённый приём в TVText на ТВ."},
            {"transport_timeout", "Истекло время ожидания телевизора."},
            {"transport_connect", "Не удалось установить соединение с ТВ."},
            {"transport_bad_reply", "Неверный ответ ТВ."},
            {"transport_incompatible", "Несовместимая версия TVText."},
            {"transport_changed_id", "Изменился идентификатор телевизора."},
            {"transport_closed", "ТВ закрыл соединение."},
            {"transport_too_many", "Сохранено слишком много телевизоров."},
            {"transport_bad_id", "Некорректный идентификатор ТВ."},
            {"transport_store_busy", "Подключения сейчас сохраняются в другом окне TVText. Повторите попытку."},
            {"discovery_failed", "Не удалось выполнить поиск. Проверьте подключение к Wi-Fi и повторите."},
            {"discovery_failed_detail", "Не удалось найти телевизоры: {0}. Проверьте подключение к Wi-Fi."}
        };

        private static readonly Dictionary<string,string> en = new Dictionary<string,string>
        {
            {"app_title", "TVText"},
            {"connection_changed", "The connection changed in another window. Restart TVText."},
            {"version", "Version {0}"},
            {"device_caption", "TELEVISIONS"},
            {"device_list_accessible", "TV list"},
            {"device_needs_pairing", "Click the “Connect” button"},
            {"rename_tv", "Rename TV"},
            {"rename", "Rename"},
            {"rename_tip", "Rename this device"},
            {"rename_heading", "TV name"},
            {"rename_hint", "This name is only shown on this device"},
            {"rename_failed", "Could not save the name"},
            {"name_required", "Enter a name with 1–80 characters on one line."},
            {"invalid_names", "The local names file is damaged. The original file was not changed."},
            {"too_many_names", "Too many local TV names are saved."},
            {"save", "Save"},
            {"cancel", "Cancel"},
            {"connect", "Connect"},
            {"paired", "Paired"},
            {"unbind", "Unpair"},
            {"confirm_unbind", "Unpair this TV from the device?"},
            {"unbind_title", "Unpair TV"},
            {"unbind_failed", "Could not unpair the TV"},
            {"unpairing", "Notifying the TV about the unpairing…"},
            {"unbind_remote_failed_title", "TV was not notified"},
            {"unbind_remote_failed", "The connection was removed from this device, but the TV did not confirm the unpairing.\n\nRemove this connection from Connected devices on the TV."},
            {"refresh_accessible", "Find TVs"},
            {"refresh_tip", "Refresh TV list"},
            {"text_prompt", "What should we send to the TV?"},
            {"input_accessible", "Text for the TV"},
            {"send", "Send to TV"},
            {"footer_hint", "Enter — send · Shift+Enter — new line"},
            {"theme_dark", "Switch to dark mode"},
            {"theme_light", "Switch to light mode"},
            {"theme_current_dark", "Dark mode is enabled"},
            {"theme_current_light", "Light mode is enabled"},
            {"televisions", "TVs"},
            {"connected_tvs", "Connected TVs"},
            {"saved_connections", "Saved connections"},
            {"no_saved_tvs", "No saved TVs yet."},
            {"delete", "Delete"},
            {"close", "Close"},
            {"confirm_delete", "Delete the connection to “{0}”?"},
            {"delete_title", "Delete connection"},
            {"delete_failed", "Could not delete the connection"},
            {"pairing", "Connecting to the TV…"},
            {"fingerprint_question", "Does the fingerprint on the TV match?\n\n{0}\n\nCompare all four groups of characters."},
            {"fingerprint_title", "Is this your TV?"},
            {"update_apk", "Update the TV APK: the device ID was not received."},
            {"changed_id", "The TV ID changed. Refresh the list and try again."},
            {"success_save_failed", "Done, but the key could not be saved to disk."},
            {"save_failed", "Could not save the connection. It is available until the app closes."},
            {"endpoint_updated_save_failed", "Address updated for this session only. Check the TV text before retrying."},
            {"too_long", "The text is too long. Maximum: 32 KB UTF-8."},
            {"pair_first", "Pair the device with a code first."},
            {"bad_request", "Invalid TVText request."},
            {"text_too_long_protocol", "The text is too long: maximum 32 KB UTF-8."},
            {"bad_reply_size", "Invalid response size."},
            {"invalid_storage", "Saved connections are damaged or unavailable. The original file was not changed."},
            {"invalid_data", "Invalid connection data."},
            {"need_pairing", "Pairing is required again. Click “Connect”."},
            {"pair_closed", "The code has expired. Get a new code in TVText on the TV."},
            {"bad_pin", "The connection code is incorrect. Check the code on the TV."},
            {"no_field", "Open the keyboard in an app on the TV, then send the text again."},
            {"receiver_disabled", "Enable TVText in the TV accessibility settings."},
            {"auth_error", "The TV could not be verified. Click “Connect” to pair again."},
            {"bad_character", "The text contains an invalid character. Paste it again."},
            {"send_no_confirmation", "No confirmation received. Check the TV text before trying again."},
            {"tv_unavailable", "The TV is unavailable. Click “Refresh” and check reception on the TV."},
            {"endpoint_updated", "TV connection restored. Check the TV text before trying again."},
            {"pair_dialog_title", "Connect TV"},
            {"pair_dialog_heading", "Connect your TV"},
            {"pair_dialog_hint", "Open TVText on your TV and choose\nnew device pairing."},
            {"pair_code", "Code shown on TV · 8 digits"},
            {"pair_code_accessible", "8-digit connection code"},
            {"transport_bad_endpoint", "Invalid TV address."},
            {"transport_bad_endpoint_refresh", "Invalid TV address. Refresh the device list."},
            {"transport_no_response", "The TV is not responding. Check Wi-Fi and reception in TVText on the TV."},
            {"transport_timeout", "Timed out waiting for the TV."},
            {"transport_connect", "Could not connect to the TV."},
            {"transport_bad_reply", "Invalid TV response."},
            {"transport_incompatible", "Incompatible TVText version."},
            {"transport_changed_id", "The TV device ID changed."},
            {"transport_closed", "The TV closed the connection."},
            {"transport_too_many", "Too many TVs are saved."},
            {"transport_bad_id", "Invalid TV device ID."},
            {"transport_store_busy", "Connections are being saved in another TVText window. Try again."},
            {"discovery_failed", "Search failed. Check Wi-Fi and try again."},
            {"discovery_failed_detail", "Could not find TVs: {0}. Check the Wi-Fi connection."}
        };

        private static readonly string path = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "TVText", "language.txt");
        internal static string Current { get; private set; }
        internal static string ButtonText { get { return Current == "en" ? "EN" : "RU"; } }

        static UiText()
        {
            Current = "ru";
            try
            {
                string saved = File.Exists(path) ? File.ReadAllText(path).Trim().ToLowerInvariant() : "";
                if (saved == "en" || saved == "ru") Current = saved;
            }
            catch { }
        }

        internal static string T(string key, params object[] args)
        {
            string value;
            Dictionary<string,string> source = Current == "en" ? en : ru;
            if (!source.TryGetValue(key, out value)) value = key;
            return args == null || args.Length == 0 ? value : String.Format(CultureInfo.CurrentCulture, value, args);
        }

        internal static string ResponseMessage(int code)
        {
            switch(code)
            {
                case 0:return String.Empty;
                case 1:return T("need_pairing");
                case 2:return T("pair_closed");
                case 3:return T("bad_pin");
                case 4:return T("no_field");
                case 5:return T("receiver_disabled");
                case 6:return T("bad_request");
                case 9:return T("send_no_confirmation");
                default:return T("transport_bad_reply");
            }
        }

        internal static void Toggle()
        {
            Current = Current == "en" ? "ru" : "en";
            try
            {
                string directory = Path.GetDirectoryName(path);
                if (!Directory.Exists(directory)) Directory.CreateDirectory(directory);
                File.WriteAllText(path, Current);
            }
            catch { }
        }
    }
}
