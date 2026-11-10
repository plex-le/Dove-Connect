package com.plexle.doveconnect;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class TaskerXmlProvider extends ContentProvider {

    public static final String AUTHORITY = "com.plexle.doveconnect.provider";
    private static final String TASKER_PKG = "net.dinglisch.android.taskerm";
    private static final String TASKER_IMPORT_ACTIVITY = "com.joaomgcd.taskerm.datashare.import.ActivityImportTaskerDataFromXml";

    @Override
    public boolean onCreate() {
        return true;
    }

    private File getSharedXmlDir() {
        Context ctx = getContext();
        if (ctx == null) return null;
        File dir = new File(ctx.getCacheDir(), "tasker_xml");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File dir = getSharedXmlDir();
        String name = uri.getLastPathSegment();
        if (dir == null || name == null) return null;
        File f = new File(dir, name);
        String[] cols = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(new Object[]{name, f.exists() ? f.length() : 0L});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "text/xml";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File dir = getSharedXmlDir();
        String name = uri.getLastPathSegment();
        if (dir == null || name == null || name.contains("..")) {
            throw new FileNotFoundException("Invalid URI: " + uri);
        }
        File file = new File(dir, name);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    /**
     * 计算 Tasker 针对当前插件包名与 EditActivity 类名的唯一 Action Code
     * Tasker 内部算法：abs("A." + pkg + "." + cls).hashCode() + 1000
     */
    public static int computeTaskerPluginActionCode(Context context) {
        String pkg = context.getPackageName();
        String cls = TaskerEditActivity.class.getName();
        String key = "A." + pkg + "." + cls;
        int h = Math.abs(key.hashCode());
        return h + Math.min(Integer.MAX_VALUE - h, 1000);
    }

    public static String buildTaskerTaskXml(
            Context context,
            String taskName,
            int taskId,
            String actionMode,
            String blurb,
            String deviceMac,
            String deviceName) {
        return "<TaskerData sr=\"\" dvi=\"1\" tv=\"6.7.8\">\n"
                + buildTaskElement(context, taskName, taskId, actionMode, blurb, deviceMac, deviceName)
                + "</TaskerData>\n";
    }

    /** 一个 Project 内打包三个任务：连接 / 断开 / 切换，一键导入 */
    public static String buildTaskerProjectXml(Context context, String projectName, int projectId, int firstTaskId) {
        int t1 = firstTaskId, t2 = firstTaskId + 1, t3 = firstTaskId + 2;
        long now = System.currentTimeMillis();
        return "<TaskerData sr=\"\" dvi=\"1\" tv=\"6.7.8\">\n"
                + "\t<Project sr=\"proj0\" ve=\"2\">\n"
                + "\t\t<cdate>" + (now - 60000L) + "</cdate>\n"
                + "\t\t<name>" + escapeXml(projectName) + "</name>\n"
                + "\t\t<tids>" + t1 + "," + t2 + "," + t3 + "</tids>\n"
                + "\t\t<id>" + projectId + "</id>\n"
                + "\t</Project>\n"
                + buildTaskElement(context, "Dove_Connect", t1, "connect", "连接 (Connect) -> 默认设备", "", "默认设备")
                + buildTaskElement(context, "Dove_Disconnect", t2, "disconnect", "断开连接 (Disconnect) -> 默认设备", "", "默认设备")
                + buildTaskElement(context, "Dove_Toggle", t3, "toggle", "切换连接状态 (Toggle) -> 默认设备", "", "默认设备")
                + "</TaskerData>\n";
    }

    private static String buildTaskElement(
            Context context,
            String taskName,
            int taskId,
            String actionMode,
            String blurb,
            String deviceMac,
            String deviceName) {

        int actionCode = computeTaskerPluginActionCode(context);
        long now = System.currentTimeMillis();
        String pkg = context.getPackageName();
        String cls = TaskerEditActivity.class.getName();
        String safeTaskName = escapeXml(taskName);
        String safeBlurb = escapeXml(blurb);
        String safeMac = escapeXml(deviceMac != null ? deviceMac : "");
        String safeDevName = escapeXml(deviceName != null ? deviceName : "默认设备");

        return "\t<Task sr=\"task" + taskId + "\">\n"
                + "\t\t<cdate>" + (now - 60000L) + "</cdate>\n"
                + "\t\t<edate>" + now + "</edate>\n"
                + "\t\t<id>" + taskId + "</id>\n"
                + "\t\t<nme>" + safeTaskName + "</nme>\n"
                + "\t\t<pri>100</pri>\n"
                + "\t\t<Action sr=\"act0\" ve=\"7\">\n"
                + "\t\t\t<code>" + actionCode + "</code>\n"
                + "\t\t\t<Bundle sr=\"arg0\">\n"
                + "\t\t\t\t<Vals sr=\"val\">\n"
                + "\t\t\t\t\t<action_mode>" + escapeXml(actionMode) + "</action_mode>\n"
                + "\t\t\t\t\t<action_mode-type>java.lang.String</action_mode-type>\n"
                + "\t\t\t\t\t<com.twofortyfouram.locale.intent.extra.BLURB>" + safeBlurb + "</com.twofortyfouram.locale.intent.extra.BLURB>\n"
                + "\t\t\t\t\t<com.twofortyfouram.locale.intent.extra.BLURB-type>java.lang.String</com.twofortyfouram.locale.intent.extra.BLURB-type>\n"
                + "\t\t\t\t\t<device_mac>" + safeMac + "</device_mac>\n"
                + "\t\t\t\t\t<device_mac-type>java.lang.String</device_mac-type>\n"
                + "\t\t\t\t\t<device_name>" + safeDevName + "</device_name>\n"
                + "\t\t\t\t\t<device_name-type>java.lang.String</device_name-type>\n"
                + "\t\t\t\t\t<net.dinglisch.android.tasker.subbundled>true</net.dinglisch.android.tasker.subbundled>\n"
                + "\t\t\t\t\t<net.dinglisch.android.tasker.subbundled-type>java.lang.Boolean</net.dinglisch.android.tasker.subbundled-type>\n"
                + "\t\t\t\t</Vals>\n"
                + "\t\t\t</Bundle>\n"
                + "\t\t\t<Str sr=\"arg1\" ve=\"3\">" + pkg + "</Str>\n"
                + "\t\t\t<Str sr=\"arg2\" ve=\"3\">" + cls + "</Str>\n"
                + "\t\t\t<Int sr=\"arg3\" val=\"0\"/>\n"
                + "\t\t\t<Int sr=\"arg4\" val=\"0\"/>\n"
                + "\t\t</Action>\n"
                + "\t</Task>\n";
    }

    public static void importProjectToTasker(Context context) {
        shareAndOpen(context, "Dove_Connect.prj.xml", buildTaskerProjectXml(context, "Dove Connect", 70, 70));
    }

    public static void importTaskToTasker(
            Context context,
            String taskName,
            int taskId,
            String actionMode,
            String blurb,
            String deviceMac,
            String deviceName) {
        String xml = buildTaskerTaskXml(context, taskName, taskId, actionMode, blurb, deviceMac, deviceName);
        shareAndOpen(context, taskName.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".tsk.xml", xml);
    }

    private static void shareAndOpen(Context context, String fileName, String xml) {
        try {
            File dir = new File(context.getCacheDir(), "tasker_xml");
            if (!dir.exists()) dir.mkdirs();
            File xmlFile = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(xmlFile, false)) {
                fos.write(xml.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            Uri uri = Uri.parse("content://" + AUTHORITY + "/" + Uri.encode(fileName));
            context.grantUriPermission(TASKER_PKG, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                Intent direct = new Intent(Intent.ACTION_VIEW);
                direct.setDataAndType(uri, "text/xml");
                direct.setClassName(TASKER_PKG, TASKER_IMPORT_ACTIVITY);
                direct.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(direct);
                return;
            } catch (Throwable ignored) {}
            Intent fallback = new Intent(Intent.ACTION_VIEW);
            fallback.setDataAndType(uri, "text/xml");
            fallback.setPackage(TASKER_PKG);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallback);
        } catch (Throwable t) {
            Toast.makeText(context, "打开 Tasker 导入失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
