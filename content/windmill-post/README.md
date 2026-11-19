# 风车邮路

`windmill-post` 是与暖灯主线独立的单章冒险，用于验证第二个内容包无需修改应用角色、场景或技能枚举即可运行。

诺拉是 15 岁的风车邮路信使。她在逆风邮站加入旅团，可在营地切换为带队伙伴，并在最终遭遇中使用一次“顺风投递”造成伤害。

可编辑权威源位于 `editable/`。成品 `.fmbook` 应直接在该目录重新打包：

```sh
cd editable
zip -q -X -r ../windmill-post-1.0.0.fmbook .
```

角色母版与场景母版位于 `art-source/`；实际包内加载版本位于 `editable/assets/`。
