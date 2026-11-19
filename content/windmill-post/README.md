# 风车邮路

`windmill-post` 是与暖灯主线独立的风车邮路冒险，用于验证第二个内容包无需修改应用角色、场景或技能枚举即可运行。

诺拉是 15 岁的风车邮路信使。她在逆风邮站加入旅团，可在营地切换为带队伙伴，并在最终遭遇中使用一次“顺风投递”造成伤害。

`windmill-post-1.0.0.fmbook` 保留 H5-05 单结局历史版本；
`windmill-post-1.1.0.fmbook` 是当前可编辑版本，在旧最终检查点后增加双结局续章。

可编辑权威源位于 `editable/`。当前成品 `.fmbook` 应直接在该目录重新打包：

```sh
cd editable
zip -q -X -r ../windmill-post-1.1.0.fmbook .
```

角色母版与场景母版位于 `art-source/`；实际包内加载版本位于 `editable/assets/`。

当前包文件 SHA-256：
`dc4b3c2d051ca59c2f9cde2154eee822420edd634e180c9570636de34844f868`。
