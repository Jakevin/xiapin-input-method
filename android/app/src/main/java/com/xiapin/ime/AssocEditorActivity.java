package com.xiapin.ime;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 編輯 assoc_user.tsv：關聯字個人頻率。
 * 入口：App 圖示 / 系統鍵盤設定 → 蝦拼設定
 */
public class AssocEditorActivity extends Activity {

    private File userFile;
    private List<AssociationDict.UserEntry> entries = new ArrayList<>();
    private EntryAdapter adapter;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assoc_editor);

        userFile = new File(getFilesDir(), "assoc_user.tsv");

        status = findViewById(R.id.txt_status);
        ListView list = findViewById(R.id.list);
        adapter = new EntryAdapter();
        list.setAdapter(adapter);

        EditText editPrefix = findViewById(R.id.edit_prefix);
        EditText editNext = findViewById(R.id.edit_next);
        EditText editCount = findViewById(R.id.edit_count);

        findViewById(R.id.btn_add).setOnClickListener(v -> {
            String prefix = editPrefix.getText().toString().trim();
            String next = editNext.getText().toString().trim();
            int count = 10;
            try {
                count = Integer.parseInt(editCount.getText().toString().trim());
            } catch (Exception ignored) {}
            if (TextUtils.isEmpty(prefix) || TextUtils.isEmpty(next)) {
                Toast.makeText(this, "請填前綴與下一字", Toast.LENGTH_SHORT).show();
                return;
            }
            if (count <= 0) count = 1;
            // 若已存在則更新
            boolean found = false;
            for (AssociationDict.UserEntry e : entries) {
                if (e.prefix.equals(prefix) && e.next.equals(next)) {
                    e.count = count;
                    found = true;
                    break;
                }
            }
            if (!found) {
                entries.add(new AssociationDict.UserEntry(prefix, next, count));
            }
            saveAndRefresh();
            editPrefix.setText("");
            editNext.setText("");
            hideKeyboard();
            Toast.makeText(this, found ? "已更新" : "已新增", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_reload).setOnClickListener(v -> {
            loadEntries();
            Toast.makeText(this, "已重新載入", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("全部清除")
                    .setMessage("確定刪除所有關聯頻率？")
                    .setPositiveButton("清除", (d, w) -> {
                        entries.clear();
                        saveAndRefresh();
                        Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        loadEntries();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEntries();
    }

    private void loadEntries() {
        entries = new ArrayList<>(AssociationDict.readUserFile(userFile));
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void saveAndRefresh() {
        AssociationDict.writeUserFile(userFile, entries);
        // 重新讀一次確保排序
        entries = new ArrayList<>(AssociationDict.readUserFile(userFile));
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void updateStatus() {
        status.setText("共 " + entries.size() + " 筆 · " + userFile.getAbsolutePath());
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private class EntryAdapter extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(AssocEditorActivity.this)
                        .inflate(R.layout.assoc_row, parent, false);
            }
            AssociationDict.UserEntry e = entries.get(position);
            TextView p = row.findViewById(R.id.row_prefix);
            TextView n = row.findViewById(R.id.row_next);
            EditText c = row.findViewById(R.id.row_count);
            Button del = row.findViewById(R.id.row_delete);

            p.setText(e.prefix);
            n.setText(e.next);

            // 避免回收 View 觸發舊 listener
            c.setOnFocusChangeListener(null);
            c.setText(String.valueOf(e.count));
            c.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) return;
                try {
                    int val = Integer.parseInt(((EditText) v).getText().toString().trim());
                    if (val != e.count) {
                        e.count = Math.max(0, val);
                        if (e.count <= 0) {
                            entries.remove(e);
                        }
                        saveAndRefresh();
                    }
                } catch (Exception ignored) {
                    ((EditText) v).setText(String.valueOf(e.count));
                }
            });

            del.setOnClickListener(v -> {
                entries.remove(e);
                saveAndRefresh();
            });

            return row;
        }
    }
}
