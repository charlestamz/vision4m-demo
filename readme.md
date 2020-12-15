## 荟诚边缘计算SDK支持人脸识别和车牌识别
# 人脸识别：
- 人脸查找
- 人脸关键点提取
- 人脸对齐
- 人脸特征提取
- 人脸库特征搜索
- 单双目活体检测
# 车牌识别
- 车牌定位
- 车牌识别
## 支持的中华人民共和国大陆车牌

|     车牌种类 | 是否支持 |
|------------:|:------:|
|          蓝 |  Y|
|          黄 | Y|
|       新能源 |Y|
|   大型新能源 |  Y|
|     教练车牌 | Y|
|     双层黄牌 | N|
|     摩托车牌 | Y|
|        警牌 | Y|
|        军牌 | Y|
|     自行车牌 | Y|
|     武警车牌 | Y|
| 双层武警牌照 |   Y|
|   港澳通行牌 |  Y|
|     普通黑牌 | Y|
|     应急车牌 | Y|
|     民航车牌 | Y|
| 使、领馆车牌 |  Y|
|        临牌 |Y|
| 低速农用车牌 |Y|

备注： Y 支持，N 不支持，E 处于评估阶段
# 1、安装facesdk.apk
安装后启动facesdk，启动后，使用激活码或支付宝购买测试版激活，支付宝1分钱可以试用
下载位置
链接： [here](./deps/facesdk-release.apk)


# 2、安装demo.apk
demo内有sdk的演示，分别为
1、装载模型
2、查找人脸
3、录入人脸
4、实时人脸比对 LiveTest
已经自带单目活体校验



