package com.xiapin.ime;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 assets/rime/* 複製到可寫目錄（Rime 需要可寫的 shared_data_dir）。
 * 只在版本號變動或檔案缺失時複製；重部署時回傳 true，呼叫端應清掉 user/build。
 */
public final class AssetDeployer {
    private static final String TAG = "AssetDeployer";

    private AssetDeployer() {}

    /** @return true 若本次有重新複製 assets（需重建 Rime binary） */
    public static boolean deploy(Context ctx, File targetDir) {
        if (!targetDir.exists()) targetDir.mkdirs();
        List<String> files = listAssetRime(ctx, "");
        String checksum = computeChecksum(ctx, files);
        // 加 schema 版本戳，確保 luna_pinyin_build 修正後一定重部署
        checksum = "v2|" + checksum;
        File mark = new File(targetDir, ".deployed_ver");
        boolean needDeploy = true;
        if (mark.exists()) {
            try {
                String prev = new String(java.nio.file.Files.readAllBytes(mark.toPath())).trim();
                needDeploy = !prev.equals(checksum);
            } catch (Exception ignored) { needDeploy = true; }
        }
        if (needDeploy) {
            for (String rel : files) {
                File out = new File(targetDir, rel);
                try {
                    copyAsset(ctx, "rime/" + rel, out);
                } catch (IOException e) {
                    Log.e(TAG, "copy failed: " + rel, e);
                }
            }
            try {
                java.nio.file.Files.write(mark.toPath(), checksum.getBytes());
            } catch (IOException ignored) {}
            Log.i(TAG, "deployed " + files.size() + " rime files to " + targetDir);
            return true;
        }
        Log.i(TAG, "rime assets unchanged, skip deploy");
        return false;
    }

    /** 刪除目錄內容（用於清掉損壞的 Rime build） */
    public static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteRecursive(k);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static String computeChecksum(Context ctx, List<String> files) {
        StringBuilder sb = new StringBuilder();
        for (String rel : files) {
            sb.append(rel).append('=');
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                try (InputStream in = ctx.getAssets().open("rime/" + rel)) {
                    byte[] buf = new byte[8192];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        md.update(buf, 0, n);
                        total += n;
                    }
                    sb.append(total).append(':');
                    byte[] d = md.digest();
                    for (byte b : d) sb.append(String.format("%02x", b));
                }
                sb.append(';');
            } catch (Exception e) {
                sb.append("err;");
            }
        }
        return sb.toString();
    }

    private static List<String> listAssetRime(Context ctx, String path) {
        List<String> result = new ArrayList<>();
        try {
            String[] entries = ctx.getAssets().list("rime/" + path);
            if (entries == null) return result;
            for (String e : entries) {
                String full = path.isEmpty() ? e : path + "/" + e;
                String[] sub = ctx.getAssets().list("rime/" + full);
                if (sub != null && sub.length > 0) {
                    result.addAll(listAssetRime(ctx, full));
                } else {
                    result.add(full);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "list assets failed", e);
        }
        return result;
    }

    private static void copyAsset(Context ctx, String assetPath, File out) throws IOException {
        InputStream in = ctx.getAssets().open(assetPath);
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        } finally {
            in.close();
        }
    }
}
