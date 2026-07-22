package com.xiapin.ime;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/** 翻譯後端：gtx 即時 / LLM OpenAI 相容（手動翻） */
public class TranslateSettingsActivity extends Activity {

    private RadioGroup rgEngine;
    private RadioButton rbGtx;
    private RadioButton rbLlm;
    private EditText editBaseUrl;
    private EditText editApiKey;
    private EditText editModel;
    private TextView txtStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translate_settings);

        rgEngine = findViewById(R.id.rg_engine);
        rbGtx = findViewById(R.id.rb_gtx);
        rbLlm = findViewById(R.id.rb_llm);
        editBaseUrl = findViewById(R.id.edit_base_url);
        editApiKey = findViewById(R.id.edit_api_key);
        editModel = findViewById(R.id.edit_model);
        txtStatus = findViewById(R.id.txt_status);
        Button btnSave = findViewById(R.id.btn_save);
        Button btnOpenRouter = findViewById(R.id.btn_preset_openrouter);
        Button btnOpenCode = findViewById(R.id.btn_preset_opencode);
        Button btnAssoc = findViewById(R.id.btn_assoc);

        load();

        btnOpenRouter.setOnClickListener(v -> {
            editBaseUrl.setText(TranslatePrefs.PRESET_OPENROUTER);
            if (editModel.getText() == null || editModel.getText().toString().trim().isEmpty()) {
                editModel.setText("openai/gpt-4o-mini");
            }
            rbLlm.setChecked(true);
        });
        btnOpenCode.setOnClickListener(v -> {
            editBaseUrl.setText(TranslatePrefs.PRESET_OPENCODE);
            rbLlm.setChecked(true);
        });
        btnSave.setOnClickListener(v -> save());
        btnAssoc.setOnClickListener(v ->
                startActivity(new Intent(this, AssocEditorActivity.class)));
    }

    private void load() {
        String engine = TranslatePrefs.getEngine(this);
        if (TranslatePrefs.ENGINE_LLM.equals(engine)) rbLlm.setChecked(true);
        else rbGtx.setChecked(true);
        editBaseUrl.setText(TranslatePrefs.getBaseUrl(this));
        editApiKey.setText(TranslatePrefs.getApiKey(this));
        editModel.setText(TranslatePrefs.getModel(this));
        txtStatus.setText(statusLine());
    }

    private void save() {
        String engine = rbLlm.isChecked() ? TranslatePrefs.ENGINE_LLM : TranslatePrefs.ENGINE_GTX;
        TranslatePrefs.setEngine(this, engine);
        TranslatePrefs.setBaseUrl(this, editBaseUrl.getText() != null ? editBaseUrl.getText().toString() : "");
        TranslatePrefs.setApiKey(this, editApiKey.getText() != null ? editApiKey.getText().toString() : "");
        TranslatePrefs.setModel(this, editModel.getText() != null ? editModel.getText().toString() : "");
        txtStatus.setText(statusLine());
        Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();
    }

    private String statusLine() {
        if (TranslatePrefs.isLlm(this)) {
            String model = TranslatePrefs.getModel(this);
            String base = TranslatePrefs.getBaseUrl(this);
            boolean hasKey = TranslatePrefs.getApiKey(this).length() > 0;
            return "目前：LLM · " + (hasKey ? "Key✓" : "Key✗") + " · " + model + "\n" + base
                    + "\n（輸入停 3 秒後自動呼叫；也可按「翻譯」）";
        }
        return "目前：免費即時 gtx（打字 debounce 後自動翻）";
    }
}
