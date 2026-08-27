local M = {}

-- 效能：只對單字／雙字加字根；候選過多時只處理前 LIMIT 個
-- comment_cache 跟著 Squirrel session 活，必須有上限，否則越用越慢
local MAX_PHRASE_CHARS = 2
local MAX_CANDS_PER_TICK = 24
local MAX_CACHE = 2048

local function script_dir()
  local source = debug.getinfo(1, "S").source or ""
  if source:sub(1, 1) == "@" then
    source = source:sub(2)
  end
  return source:match("^(.*)/[^/]+$") or "."
end

local function utf8_chars(text)
  local chars = {}
  for ch in text:gmatch("[%z\1-\127\194-\244][\128-\191]*") do
    chars[#chars + 1] = ch
  end
  return chars
end

local function utf8_codepoint(char)
  local b1, b2, b3 = char:byte(1, 3)
  if not b1 then
    return nil
  elseif b1 < 0x80 then
    return b1
  elseif b1 < 0xe0 and b2 then
    return (b1 - 0xc0) * 0x40 + (b2 - 0x80)
  elseif b1 < 0xf0 and b2 and b3 then
    return (b1 - 0xe0) * 0x1000 + (b2 - 0x80) * 0x40 + (b3 - 0x80)
  end
  return nil
end

local function is_cjk(char)
  local cp = utf8_codepoint(char)
  -- 常用區即可（與 Android 顯示過濾對齊，略過 ExtA 可加速）
  return cp and cp >= 0x4E00 and cp <= 0x9fff
end

-- 只保留「最短碼」：省記憶體、查表 O(1)
local function add_root(roots, text, code)
  local existing = roots[text]
  if not existing or #code < #existing then
    roots[text] = code
  end
end

local function read_lookup(path, roots)
  local file = io.open(path, "r")
  if not file then
    return 0
  end

  local count = 0
  local in_data = false
  for raw in file:lines() do
    if raw == "..." then
      in_data = true
    elseif in_data and raw ~= "" and raw:sub(1, 1) ~= "#" then
      local tab = raw:find("\t", 1, true)
      if tab then
        local text = raw:sub(1, tab - 1):gsub("^%s+", ""):gsub("%s+$", "")
        local rest = raw:sub(tab + 1)
        local code = rest:match("^([^%s\t]+)")
        if text and code and not code:find("[,%.]") then
          -- 單字
          local chars = utf8_chars(text)
          if #chars == 1 and is_cjk(chars[1]) then
            local c = code
            if c:sub(1, 1) == "~" then
              c = c:sub(2)
            end
            add_root(roots, text, c)
            count = count + 1
          end
        end
      end
    end
  end

  file:close()
  return count
end

local function comment_for_text(text, roots)
  -- 純 ASCII 直接跳過（英文候選）
  if text:find("^[%z\1-\127]+$") then
    return nil
  end

  local chars = utf8_chars(text)
  local n = #chars
  if n == 0 or n > MAX_PHRASE_CHARS then
    return nil
  end

  if n == 1 then
    if not is_cjk(chars[1]) then
      return nil
    end
    return roots[chars[1]]
  end

  -- 雙字：各取最短碼，用 · 連接
  local parts = {}
  for _, ch in ipairs(chars) do
    if not is_cjk(ch) then
      return nil
    end
    local code = roots[ch]
    if not code then
      return nil
    end
    parts[#parts + 1] = code
  end
  return table.concat(parts, "·")
end

function M.init(env)
  local roots = {}
  local dir = script_dir()
  local total = 0

  -- 優先讀已過濾的 xiapin_liur（小、快）；沒有才退回 openxiami 原表
  local liur = dir .. "/../xiapin_liur.dict.yaml"
  local n = read_lookup(liur, roots)
  total = total + n
  if n == 0 then
    total = total + read_lookup(dir .. "/../openxiami_TCJP.dict.yaml", roots)
    total = total + read_lookup(dir .. "/../openxiami_TradExt.dict.yaml", roots)
  end

  env.roots = roots
  env.comment_cache = {}
  env.comment_cache_size = 0
  -- 可選：log 初始化規模（Squirrel 日誌）
  -- log.info(string.format("[boshiamy_comment] loaded %d root entries", total))
end

local function remember_comment(env, cache, text, comment)
  if not comment then
    return
  end
  if (env.comment_cache_size or 0) >= MAX_CACHE then
    cache = {}
    env.comment_cache = cache
    env.comment_cache_size = 0
  end
  if cache[text] == nil then
    cache[text] = comment
    env.comment_cache_size = (env.comment_cache_size or 0) + 1
  end
  return cache
end

function M.func(input, env)
  local roots = env.roots or {}
  local cache = env.comment_cache
  if not cache then
    cache = {}
    env.comment_cache = cache
    env.comment_cache_size = 0
  end
  local seen = 0

  for cand in input:iter() do
    seen = seen + 1
    -- 超過本頁合理數量就不再算 comment，直接透傳（保順序）
    if seen <= MAX_CANDS_PER_TICK and cand.text and cand.text ~= "" then
      local comment = cache[cand.text]
      if comment == nil then
        comment = comment_for_text(cand.text, roots)
        cache = remember_comment(env, cache, cand.text, comment) or cache
      end
      if comment and comment ~= "" then
        local target = cand.get_genuine and cand:get_genuine() or cand
        local existing = target.comment or cand.comment or ""
        if existing == "" then
          target.comment = comment
        elseif not existing:find(comment, 1, true) then
          -- 已有 comment 就不再串長字串（省字串配置）
        end
      end
    end
    yield(cand)
  end
end

return M
