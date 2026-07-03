local M = {}

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

local function add_root(roots, text, code)
  local existing = roots[text]
  if not existing then
    existing = {}
    roots[text] = existing
  end

  for _, item in ipairs(existing) do
    if item == code then
      return
    end
  end
  existing[#existing + 1] = code
end

local function read_lookup(path, roots)
  local file = io.open(path, "r")
  if not file then
    return
  end

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
        local chars = utf8_chars(text)
        local is_single_char = #chars == 1

        if is_single_char and code and not code:find("[,%.]") then
          local only_char = utf8_codepoint(chars[1])

          if not (only_char and only_char >= 0x3040 and only_char <= 0x30ff) then
            add_root(roots, text, code)
          end
        end
      end
    end
  end

  file:close()
end

local function compact_codes(codes)
  if not codes or #codes == 0 then
    return nil
  end
  if #codes == 1 then
    return codes[1]
  end
  return table.concat(codes, " / ", 1, math.min(#codes, 3))
end

local function comment_for_text(text, roots)
  local chars = utf8_chars(text)
  if #chars == 0 then
    return nil
  end

  if #chars == 1 then
    return compact_codes(roots[chars[1]])
  end

  local parts = {}
  for _, ch in ipairs(chars) do
    local codes = roots[ch]
    if not codes or #codes == 0 then
      return nil
    end
    parts[#parts + 1] = codes[1]
  end
  return table.concat(parts, "·")
end

function M.init(env)
  local roots = {}
  local dir = script_dir()

  read_lookup(dir .. "/../xiapin_liur.dict.yaml", roots)
  read_lookup(dir .. "/../openxiami_TCJP.dict.yaml", roots)
  read_lookup(dir .. "/../openxiami_TradExt.dict.yaml", roots)

  env.roots = roots
end

function M.func(input, env)
  local roots = env.roots or {}
  for cand in input:iter() do
    if cand.text and cand.text ~= "" then
      local comment = comment_for_text(cand.text, roots)
      if comment and comment ~= "" then
        local target = cand.get_genuine and cand:get_genuine() or cand
        local existing = target.comment or cand.comment or ""
        if existing == "" then
          target.comment = comment
        elseif not existing:find(comment, 1, true) then
          target.comment = existing .. "  " .. comment
        end
      end
    end
    yield(cand)
  end
end

return M
