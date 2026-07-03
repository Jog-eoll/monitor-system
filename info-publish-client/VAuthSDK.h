#ifndef __VAuthSDK_H__
#define __VAuthSDK_H__

#ifdef WIN32
	#ifdef VAuthSDK_EXPORTS
		#define VAuthSDK_API __declspec(dllexport)
	#else
		#define VAuthSDK_API __declspec(dllimport)
	#endif // VAuthSDK_EXPORTS
	#define VAuthSDK_CALLBACK __stdcall
	#define VAuthSDK_METHOD __stdcall
#else
	#define VAuthSDK_API
	#define VAuthSDK_CALLBACK
	#define VAuthSDK_METHOD
#endif

#if defined(__linux__)

#ifndef TRUE
#define TRUE 1
#endif
#ifndef FALSE
#define FALSE 0
#endif

#endif

typedef int							BOOL;

#ifdef __cplusplus
extern "C"
{
#endif

	/// 初始化.
	/// @return TRUE-成功，FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_Init();

	/// 释放SDK资源.
	/// @return TRUE-成功，FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_Cleanup();

	/// 释放内存
	/// @param[in] pObj	 释放的对象
	VAuthSDK_API void VAuthSDK_METHOD VAuth_Free(void* pObj);

	/// 获取错误代码.
	/// @return 错误码
	VAuthSDK_API int VAuthSDK_METHOD VAuth_GetLastError();

	/// 根据错误代码获取错误信息
	/// @param[in] nError 错误代码
	/// @return 错误信息
	VAuthSDK_API const char * VAuthSDK_METHOD VAuth_GetErrorText(int nError);

	/// 列表UKey信息
	/// @param[out] pReply 返回结果。SDK分配空间, 用完需要VAuth_Free释放
	/// 结果格式: json
	///  [
	///   {
	///	  "name":"设备名",
	///	  "label":"设备标签",
	///	  "sn":"设备序列号",
	///	  "cerSn":"证书序列号",
	///	  "cerId":"证书ID 格式: 证书用户ID_密码模块ID",
	///	  "path":"容器路径 格式: 设备名/应用名/容器名"
	///   },
	///    ...
	///  ]
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_ListUkeyInfos(char* &pReply);

	/// UKey事件回调函数.
	/// @param[in] type 事件类型。 1: 插入 2: 拔出
	/// @param[in] name UKey设备名
	/// @param[in] msg 事件信息。
	/// @param[in] dwUser 回调参数
	/// @return 0 - 回调执行成功
	typedef int (VAuthSDK_CALLBACK *FnUkeyEventCallBack)(int type, const char *name, const char *msg, void* dwUser);

	/// 注册UKey事件
	/// @param[in] fnEventCallBack 事件回调函数
	/// @param[in] dwUser 回调参数
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_SetUkeyEventCallback(FnUkeyEventCallBack fnEventCallBack, void* dwUser);

	/// 打开UKey
	/// @param[in] path UKey路径。VAuth_ListUkeyInfos中的返回值。
	/// @param[in] password UKey密码
	/// @param[in] authId 认证ID
	/// @return 设备句柄。>=0: 句柄 < 0: 错误码
	VAuthSDK_API int VAuthSDK_METHOD VAuth_OpenUkey(const char *path, const char *password, const char *authId);

	/// 打开SDF密码卡
	/// @param[in] password 设备密码
	/// @param[in] authId 认证ID
	/// @return 设备句柄。>=0: 句柄 < 0: 错误码
	VAuthSDK_API int VAuthSDK_METHOD VAuth_OpenSDF(const char *password, const char *authId);

	/// 关闭句柄
	/// @param[in] handle 打开相关句柄的返回值
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_CloseHandle(int handle);

	/// 设置认证服务信息 (认证方使用)
	/// @param[in] handle 打开相关句柄的返回值
	/// @param[in] id 认证服务ID
	/// @param[in] signCer 认证服务签名证书信息
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_SetAuthServerInfo(int handle, const char *id, const char *signCer);

	/// 生成认证请求 (认证方使用)
	/// @param[in] handle 设备句柄
	/// @param[out] pReply 返回认证请求消息。SDK分配空间, 用完需要VAuth_Free释放
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_BuildAuthReq(int handle, char* &pReply);

	/// 生成认证消息 (认证方使用)
	/// @param[in] handle 设备句柄
	/// @param[in] respInfo 认证服务返回的认证请求回复消息
	/// @param[out] pReply 返回认证消息。SDK分配空间, 用完需要VAuth_Free释放
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_BuildAuthInfo(int handle, const char *respInfo, char* &pReply);

	/// 检查认证结果 (认证方使用)
	/// @param[in] handle 设备句柄
	/// @param[in] respInfo 认证服务返回的认证结果消息
	/// @param[out] pError 认证错误信息。非空时为认证错误信息。需要上报给认证服务。SDK分配空间, 用完需要VAuth_Free释放
	/// @return TRUE-认证成功 FALSE-认证失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_CheckAuthResult(int handle, const char *respInfo, char* &pError);

	/// 解析认证请求 (认证服务使用)
	/// @param[in] handle 设备句柄
	/// @param[in] authId 认证用户或设备ID
	/// @param[in] reqInfo 认证请求信息
	/// @param[out] pReply 返回认证请求回复消息。SDK分配空间, 用完需要VAuth_Free释放
	/// @return TRUE-认证成功 FALSE-认证失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_ParseAuthReq(int handle, const char *authId, const char *reqInfo, char* &pReply);

	/// 解析认证消息 (认证服务使用)
	/// @param[in] handle 设备句柄
	/// @param[in] authId 认证用户或设备ID
	/// @param[in] signCer 认证用户或设备的签名证书信息
	/// @param[in] reqInfo 认证信息
	/// @param[out] pReply 返回认证结果。SDK分配空间, 用完需要VAuth_Free释放
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_ParseAuthInfo(int handle, const char *authId, const char *signCer, const char *reqInfo, char* &pReply);

	/// 解析认证错误消息 (认证服务使用)
	/// @param[in] handle 设备句柄
	/// @param[in] respInfo 认证方返回的认证错误信息
	/// @param[out] pError 返回认证错误信息。需要上报给认证服务。SDK分配空间, 用完需要VAuth_Free释放
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_ParseAuthError(int handle, const char *respInfo, char* &pError);

	/// 数据加密
	/// @param[in] handle 设备句柄
	/// @param[in] isSign 是否数据签名
	/// @param[in] pData 待加密数据
	/// @param[in] dataLen 待加密数据长度
	/// @param[out] pOutData 返回加密数据。SDK分配空间, 用完需要VAuth_Free释放
	/// @param[out] outLen 返回加密数据长度
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_EncryptData(int handle, BOOL isSign, const unsigned char *pData, unsigned int dataLen, unsigned char * &pOutData, unsigned int &outLen);

	/// 查询密钥回调。需要在回调中调用VAuth_SetKey设置密钥，否则当前解密失败。
	/// @param[in] handle 设备句柄
	/// @param[in] id 用户或设备认证ID
	/// @param[in] ver 密钥版本
	/// @param[in] dwUser 回调参数
	/// @return 0 - 回调执行成功
	typedef int (VAuthSDK_CALLBACK *FnQueryKeyCallBack)(int handle, const char *id, const char *ver, void* dwUser);

	/// 设置查询密钥回调。
	/// @param[in] fnCallBack 查询密钥回调函数
	/// @param[in] dwUser 回调参数
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_SetQueryKeyCallback(FnQueryKeyCallBack fnCallBack, void* dwUser);

	/// 设置密钥
	/// @param[in] handle 设备句柄
	/// @param[in] id 用户或设备认证ID
	/// @param[in] ver 密钥版本
	/// @param[in] key 密钥BASE64信息
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_SetKey(int handle, const char *id, const char *ver, const char *key);

	/// 查询证书回调。需要在回调中调用VAuth_SetCer设置证书，否则当前验签失败。
	/// @param[in] id 用户或设备认证ID
	/// @param[in] type 证书类型 1: 签名证书
	/// @param[in] dwUser 回调参数
	/// @return 0 - 回调执行成功
	typedef int (VAuthSDK_CALLBACK *FnQueryCerCallBack)(const char *id, int type, void* dwUser);

	/// 设置查询证书回调。
	/// @param[in] fnCallBack 查询证书回调函数
	/// @param[in] dwUser 回调参数
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_SetQueryCerCallback(FnQueryCerCallBack fnCallBack, void* dwUser);

	/// 设置证书
	/// @param[in] id 用户或设备认证ID
	/// @param[in] type 证书类型 1: 签名证书
	/// @param[in] cer 证书信息
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_SetCer(const char *id, int type, const char *cer);

	/// 数据解密
	/// @param[in] handle 设备句柄
	/// @param[in] isVerify 是否数据验签
	/// @param[in] pData 加密数据
	/// @param[in] dataLen 加密数据长度
	/// @param[out] pOutData 返回解密数据。SDK分配空间, 用完需要VAuth_Free释放
	/// @param[out] outLen 返回解密数据长度
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_DecryptData(int handle, BOOL isVerify, const unsigned char *pData, unsigned int dataLen, unsigned char * &pOutData, unsigned int &outLen);

	/// 密钥信息回调。
	/// @param[in] id 用户或设备认证ID
	/// @param[in] ver 密钥版本
	/// @param[in] key 密钥BASE64信息
	/// @param[in] dwUser 回调参数
	/// @return 0 - 回调执行成功
	typedef int (VAuthSDK_CALLBACK *FnKeyCallBack)(const char *id, const char *ver, const char *key, void* dwUser);

	/// 设置密钥信息回调。
	/// @param[in] fnCallBack 密钥回调函数
	/// @param[in] dwUser 回调参数
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_SetKeyCallback(FnKeyCallBack fnCallBack, void* dwUser);

	/// 密钥加密
	/// @param[in] handle 设备句柄
	/// @param[in] key 密钥BASE64信息
	/// @param[in] cer 请求密钥方的证书信息
	/// @param[out] pReply 返回加密密钥的BASE64信息。SDK分配空间, 用完需要VAuth_Free释放
	/// @return TRUE-成功 FALSE-失败
	VAuthSDK_API BOOL VAuthSDK_METHOD VAuth_EncryptKey(int handle, const char *key, const char *cer, char* &pReply);

#ifdef __cplusplus
}
#endif

#endif //!_ZPLAYERSDK_H_
