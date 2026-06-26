# 蝦拼輸入法 Rime Package

## Install

Install Squirrel / 鼠鬚管 first, then run:

```bash
bash install.sh
```

After installation, choose Squirrel from the macOS input menu and click `重新部署`.

## Schemas

- `蝦拼`: Pinyin fallback, original shape/root demo codes, English candidates, and optional local Boshiamy-style table.
- `蝦拼英文`: English candidate mode for prefixes such as `veri`.

## Optional liur_Trad.dict.yaml

This package does not include `liur_Trad.dict.yaml` because the provided file does not include clear public license metadata.

If you have your own legal copy, place it beside `install.sh` before installing:

```text
install.sh
liur_Trad.dict.yaml
rime/
```

The installer will import it locally as `xiapin_liur`.

## Test Codes

```text
ni       -> 你 / 尼
tai      -> 台
m:box    -> 口
veri     -> verify / verified / verification
thankyou -> thank you
```

With optional `liur_Trad.dict.yaml`:

```text
a   -> 對
aaa -> 鑫
bn  -> 人
ix  -> 我
oo  -> 口
```
