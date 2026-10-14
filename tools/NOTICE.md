# 第三方数据来源 / Third-party data sources

## tools/source/tangshi300.json

- **内容**：通行本《唐诗三百首》（蘅塘退士编）正文，按体裁分 7 组，共 320 首。
- **来源**：[chinese-poetry/chinese-poetry](https://github.com/chinese-poetry/chinese-poetry) 仓库 `蒙学/tangshi300.json`
- **许可**：MIT License（该仓库根目录 LICENSE）
- **形态**：繁体中文，含「一作：」「通：」等异文注记（括号内），由 `tools/build_corpus.py` 清洗并转简体。

清洗与转换流程见 `tools/build_corpus.py` 顶部说明。原始文件保持原样入库，不做原地修改，便于复核与重跑。

> 字体（`app/src/main/res/font/wenkai.ttf`）等随包发布资产的第三方声明见 [app/THIRD-PARTY-NOTICES.md](../app/THIRD-PARTY-NOTICES.md)。
