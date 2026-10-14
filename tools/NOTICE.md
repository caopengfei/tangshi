# 第三方数据来源 / Third-party data sources

## tools/source/tangshi300.json

- **内容**：通行本《唐诗三百首》（蘅塘退士编）正文，按体裁分 7 组，共 320 首。
- **来源**：[chinese-poetry/chinese-poetry](https://github.com/chinese-poetry/chinese-poetry) 仓库 `蒙学/tangshi300.json`
- **许可**：MIT License（该仓库根目录 LICENSE）
- **形态**：繁体中文，含「一作：」「通：」等异文注记（括号内），由 `tools/build_corpus.py` 清洗并转简体。

清洗与转换流程见 `tools/build_corpus.py` 顶部说明。原始文件保持原样入库，不做原地修改，便于复核与重跑。

## app/src/main/res/font/wenkai.ttf

- **内容**：霞鹜文楷 LXGW WenKai v1.522，界面与诗体显示字体。
- **来源**：[lxgw/LxgwWenKai](https://github.com/lxgw/LxgwWenKai)（上游衍生自 [The Klee Project](https://github.com/fontworks-fonts/Klee)）
- **许可**：SIL Open Font License 1.1（<https://openfontlicense.org>）
- **版权**：Copyright 2021-2026 LXGW；Copyright 2020 The Klee Project Authors

衍生使用：`app/src/main/res/drawable/app_banner.xml` 的字标「唐诗三百首」由 `tools/make_banner.py` 从该字体提取字形轮廓转成矢量路径（只取轮廓，不嵌字体文件本身）。
