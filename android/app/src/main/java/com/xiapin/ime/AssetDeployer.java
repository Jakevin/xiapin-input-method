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
 * 只在版本號變動或檔案缺失時複製。
 * @return true 若本次有重新部署（呼叫端應清 user/build 強制 Rime 重編）
 */
public final class AssetDeployer {
    private static final String TAG = "AssetDeployer";

    private AssetDeployer() {}

    public static boolean deploy(Context ctx, File targetDir) {
        if (!targetDir.exists()) targetDir.mkdirs();
        // 用 assets 內容的總 checksum 決定是否重部署，確保任何 schema/dict 變動都生效
        List<String> files = listAssetRime(ctx, "");
        // v2| 前綴：舊 mark 一律失效，強制重部署一次（修復壞掉的 prism/build）
        String checksum = "v2|" + computeChecksum(ctx, files);
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
                    copyAsset(ctx, "rime/" + rel, out); // 總是覆蓋
                } catch (IOException e) {
                    Log.e(TAG, "copy failed: " + rel, e);
                }
            }
            try {
                java.nio.file.Files.write(mark.toPath(), checksum.getBytes());
            } catch (IOException ignored) {}
            Log.i(TAG, "deployed " + files.size() + " rime files to " + targetDir);
            return true;
        } else {
            Log.i(TAG, "rime assets unchanged, skip deploy");
            return false;
        }
    }

    /** 遞迴刪除目錄（用於清 rime_user/build 強制重編）。 */
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

    /** 全檔 MD5 + 檔名，確保任何 schema/dict 變動都會重部署。 */
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
