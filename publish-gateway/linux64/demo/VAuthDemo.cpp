// VAuthDemo.cpp : 此文件包含 "main" 函数。程序执行将在此处开始并结束。
//


#include <iostream>
#include <fstream>
#include <sstream>
#include <cstring>
#include <map>
#include "VAuthSDK.h"

#ifndef TRUE
#define TRUE 1
#endif
#ifndef FALSE
#define FALSE 0
#endif


class AuthDatas
{
public:
    static int OnKeyCallBack(const char *id, const char *ver, const char *key, void *dwUser)
    {
        std::cout << "Key. id: " << id << " ver: " << ver << " key: " << key << std::endl;

        AuthDatas *pThis = (AuthDatas *)dwUser;

        std::stringstream keyIndex;
        keyIndex << id << "_" << ver;
        pThis->m_keys[keyIndex.str()] = key;

        return 0;
    }

    BOOL Open(int serverHandle)
    {
        if (!VAuth_SetKeyCallback(OnKeyCallBack, this))
        {
            std::cout << "Set key callback fail! err:" << std::endl;
            return FALSE;
        }

        m_handle = serverHandle;
        return TRUE;
    }

    BOOL AddCer(const std::string &authId, int type, const std::string &cerPath)
    {
        std::ifstream certFile(cerPath);
        if (certFile.fail())
        {
            std::cout << "Open client sign cer fail! path:" << cerPath << std::endl;
            return FALSE;
        }

        std::stringstream certStream;
        certStream << certFile.rdbuf();
        std::string signCer = certStream.str();
        if (signCer.empty())
        {
            std::cout << "Read client sign cer fail! path:" << cerPath << std::endl;
            return FALSE;
        }

        std::stringstream cerIndex;
        cerIndex << authId << "_" << 1;

        m_cers[cerIndex.str()] = signCer;
        return TRUE;
    }

    BOOL QueryKey(const std::string &authId, const std::string &srcId, const std::string &ver, std::string &key)
    {
        std::stringstream keyIndex;
        keyIndex << srcId << "_" << ver;

        auto it = m_keys.find(keyIndex.str());
        if (it == m_keys.end())
        {
            std::cout << "Query key fail!" << std::endl;
            return FALSE;
        }

        const std::string &srcKey = it->second;

        std::stringstream cerIndex;
        cerIndex << authId << "_" << 1;

        auto cerIt = m_cers.find(cerIndex.str());
        if (cerIt == m_cers.end())
        {
            std::cout << "Query cer fail!" << std::endl;
            return FALSE;
        }

        const std::string &queryCer = cerIt->second;

        char *pKey = NULL;
        if (!VAuth_EncryptKey(m_handle, srcKey.c_str(), queryCer.c_str(), pKey))
        {
            std::cout << "Encrypt key fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }

        key = pKey;
        VAuth_Free(pKey);
        return TRUE;
    }

    BOOL QueryCer(const std::string &authId, int type, std::string &cer)
    {
        std::stringstream cerIndex;
        cerIndex << authId << "_" << type;

        auto cerIt = m_cers.find(cerIndex.str());
        if (cerIt == m_cers.end())
        {
            std::cout << "Query cer fail!" << std::endl;
            return FALSE;
        }

        cer = cerIt->second;
        return TRUE;
    }

private:
    int m_handle = -1;
    std::map<std::string, std::string> m_keys;
    std::map<std::string, std::string> m_cers;
};

class AuthClient
{
public:
    AuthClient(const std::string &authId, const std::string &password, const std::string &mode)
        : m_authId(authId)
        , m_password(password)
        , m_mode(mode)
    {
    }

    ~AuthClient()
    {
        if (m_handle >= 0)
            VAuth_CloseHandle(m_handle);
    }

    static int OnUkeyEventCallBack(int type, const char *name, const char *msg, void *dwUser)
    {
        std::cout << "Ukey event. type:" << type << " name:" << name << std::endl;
        return 0;
    }

    BOOL ListDev()
    {
        if (m_mode == "ukey")
        {
            BOOL isSuccess = VAuth_SetUkeyEventCallback(OnUkeyEventCallBack, this);
            if (!isSuccess)
            {
                std::cout << "Set ukey event fail! err:" << VAuth_GetLastError() << std::endl;
                return FALSE;
            }

            char *pReply = NULL;
            isSuccess = VAuth_ListUkeyInfos(pReply);
            if (!isSuccess)
            {
                std::cout << "List ukey fail! err:" << VAuth_GetLastError() << std::endl;
                return FALSE;
            }

            if (!pReply)
            {
                std::cout << "No ukey!" << std::endl;
                return FALSE;
            }

            m_ukInfo = pReply;
            std::cout << "key: " << pReply << std::endl;
            VAuth_Free(pReply);
        }

        return TRUE;
    }

    BOOL Open()
    {
        if (m_mode == "ukey")
        {
			if (m_authId.empty())
			{
				const std::string beginFlag = "\"cerId\":\"";
				size_t beginPos = m_ukInfo.find(beginFlag);
				beginPos += beginFlag.size();
				size_t endPos = m_ukInfo.find_first_of("\"_", beginPos);
				m_authId = m_ukInfo.substr(beginPos, endPos - beginPos);
			}

			const std::string beginFlag = "\"path\":\"";
			size_t beginPos = m_ukInfo.find(beginFlag);
			beginPos += beginFlag.size();
			size_t endPos = m_ukInfo.find("\"", beginPos);
			std::string ukPath = m_ukInfo.substr(beginPos, endPos - beginPos);

			m_handle = VAuth_OpenUkey(ukPath.c_str(), m_password.c_str(), m_authId.c_str());
        }
        else
        {
            m_handle = VAuth_OpenSDF(m_password.c_str(), m_authId.c_str());
        }

		if (m_handle < 0)
		{
			std::cout << "Open fail! err:" << m_handle << std::endl;
			return FALSE;
		}

        return TRUE;
    }

    BOOL AuthReq(const std::string &serverId, const std::string &serverCerPath, char * &pReq)
    {
        std::ifstream certFile(serverCerPath);
        if (certFile.fail())
        {
            std::cout << "Open sign cer fail! path:" << serverCerPath << std::endl;
            return FALSE;
        }

        std::stringstream certStream;
        certStream << certFile.rdbuf();
        std::string signCer = certStream.str();
        if (signCer.empty())
        {
            std::cout << "Read sign cer fail! path:" << serverCerPath << std::endl;
            return FALSE;
        }

        BOOL isSuccess = VAuth_SetAuthServerInfo(m_handle, serverId.c_str(), signCer.c_str());
        if (!isSuccess)
        {
            std::cout << "Set server info fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }
                
        isSuccess = VAuth_BuildAuthReq(m_handle, pReq);
        if (!isSuccess)
        {
            std::cout << "Build req fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }

        return TRUE;
    }

    BOOL Auth(const char *pServerResp, char * &pReq)
    {
        BOOL isSuccess = VAuth_BuildAuthInfo(m_handle, pServerResp, pReq);
        if (!isSuccess)
        {
            std::cout << "Build auth fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }

        return TRUE;
    }

    BOOL CheckResult(const char *pServerResp, char * &pError)
    {
        BOOL isSuccess = VAuth_CheckAuthResult(m_handle, pServerResp, pError);
        if (!isSuccess)
        {
            std::cout << "Check auth result fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }

        return TRUE;
    }

    BOOL EncryptData(const unsigned char *pData, unsigned int dataLen, unsigned char * &pOutData, unsigned int &outLen)
    {
        BOOL isSuccess = VAuth_EncryptData(m_handle, TRUE, pData, dataLen, pOutData, outLen);
        if (!isSuccess)
        {
            std::cout << "Encrypt data fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }

        return TRUE;
    }

    BOOL EncryptPackData(const unsigned char *pData, unsigned int dataLen, unsigned char * &pOutData, unsigned int &outLen)
    {
        BOOL isSuccess = VAuth_EncryptPackData(m_handle, TRUE, pData, dataLen, pOutData, outLen);
        if (!isSuccess)
        {
            std::cout << "Encrypt pack data fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }

        {
            const std::string outFileName = "pack.enc";
            std::ofstream outFile(outFileName, std::ios::binary | std::ios::trunc);
            if (!outFile)
            {
                std::cout << "Write file fail! path:" << outFileName << std::endl;
                return FALSE;
            }

            outFile.write((char *)pOutData, outLen);
        }

        return TRUE;
    }

    BOOL EncryptFileData(const std::string &filePath, unsigned char * &pOutData, unsigned int &outLen)
    {
        std::ifstream file(filePath, std::ios::binary | std::ios::ate);
        if (!file)
        {
            std::cout << "Read file fail! path:" << filePath << std::endl;
            return FALSE;
        }

        // 获取文件大小
        std::streamsize size = file.tellg();
        // 回到文件开头
        file.seekg(0, std::ios::beg);

        char *pFileData = new char[size];
        if (!pFileData)
        {
            std::cout << "Alloc mem fail! path:" << filePath << std::endl;
            return FALSE;
        }

        file.read(pFileData, size);

        BOOL isSuccess = FALSE;
        do
        {
			isSuccess = VAuth_EncryptFileData(m_handle, TRUE, (unsigned char *)pFileData, size, pOutData, outLen);
			if (!isSuccess)
			{
				std::cout << "Encrypt file data fail! err:" << VAuth_GetLastError() << std::endl;
                break;
			}

            isSuccess = TRUE;
        } while (false);

        if (pFileData)
            delete [] pFileData;

        {
            const std::string outFileName = "trans.ps";
            std::ofstream outFile(outFileName, std::ios::binary | std::ios::trunc);
            if (!outFile)
            {
                std::cout << "Write file fail! path:" << outFileName << std::endl;
                return FALSE;
            }

            outFile.write((char *)pOutData, outLen);
        }

        return isSuccess;
    }

public:
    std::string m_mode;
    std::string m_authId;
    std::string m_password;

    // ukey
    std::string m_ukInfo;

    int m_handle = -1;
};

class AuthServer
{
public:
    AuthServer(const std::string &id, const std::string &password, const std::string &mode, AuthDatas *pDatas)
        : m_id(id)
        , m_password(password)
        , m_mode(mode)
        , m_pDatas(pDatas)
    {
    }

    ~AuthServer()
    {
        if (m_handle >= 0)
            VAuth_CloseHandle(m_handle);
    }

    static int OnQueryCerCallBack(const char *id, int type, void *dwUser)
    {
        std::cout << "Query cer. id: " << id << " type: " << type << std::endl;

        AuthServer *pThis = (AuthServer *)dwUser;
        std::string cer;
        if (!pThis->m_pDatas->QueryCer(id, type, cer))
            return -1;

        if (!VAuth_SetCer(id, type, cer.c_str()))
        {
            std::cout << "Set cer fail! err:" << VAuth_GetLastError() << std::endl;
            return -1;
        }
        
        std::cout << "Ser cer success. id: " << id << " type: " << type << std::endl;
        return 0;
    }

    static int OnQueryKeyCallBack(int handle, const char *id, const char *ver, void *dwUser)
    {
        std::cout << "Query key. id: " << id << " ver: " << ver << std::endl;

        AuthServer *pThis = (AuthServer *)dwUser;
        std::string key;
        if (!pThis->m_pDatas->QueryKey(pThis->m_id, id, ver, key))
            return -1;

        if (!VAuth_SetKey(pThis->m_handle, id, ver, key.c_str()))
        {
            std::cout << "Set key fail! err:" << VAuth_GetLastError() << std::endl;
            return -1;
        }

        std::cout << "Ser key success. id: " << id << " ver: " << ver << std::endl;
        return 0;
    }

    BOOL ListDev()
    {
        if (m_mode == "ukey")
        {
            char *pReply = NULL;
            BOOL isSuccess = VAuth_ListUkeyInfos(pReply);
            if (!isSuccess)
            {
                std::cout << "List server ukey fail! err:" << VAuth_GetLastError() << std::endl;
                return FALSE;
            }

            if (!pReply)
            {
                std::cout << "No server ukey!" << std::endl;
                return FALSE;
            }

            m_ukInfo = pReply;
            std::cout << "key: " << pReply << std::endl;
            VAuth_Free(pReply);
        }

        return TRUE;
    }

    BOOL Open()
    {
        if (m_mode == "ukey")
        {
            const std::string beginFlag = "\"path\":\"";
            size_t beginPos = m_ukInfo.find(beginFlag);
            beginPos += beginFlag.size();
            size_t endPos = m_ukInfo.find("\"", beginPos);
            std::string ukPath = m_ukInfo.substr(beginPos, endPos - beginPos);

            m_handle = VAuth_OpenUkey(ukPath.c_str(), m_password.c_str(), m_id.c_str());
        }
        else
        {
            m_handle = VAuth_OpenSDF(m_password.c_str(), m_id.c_str());
        }

        if (m_handle < 0)
        {
            std::cout << "Open server fail! err:" << m_handle << std::endl;
            return FALSE;
        }

        if (!VAuth_SetQueryKeyCallback(OnQueryKeyCallBack, this))
        {
            std::cout << "Set query key callback fail! err:" << m_handle << std::endl;
            return FALSE;
        }

        if (!VAuth_SetQueryCerCallback(OnQueryCerCallBack, this))
        {
            std::cout << "Set query cer callback fail! err:" << m_handle << std::endl;
            return FALSE;
        }

        return TRUE;
    }

    BOOL ParseAuthReq(const std::string &authId, const char *pReq, char * &pResp)
    {
        BOOL isSuccess = VAuth_ParseAuthReq(m_handle, authId.c_str(), pReq, pResp);
        if (!isSuccess)
        {
            std::cout << "Parse auth req fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }

        return TRUE;
    }

    BOOL ParseAuth(const std::string &authId, const std::string &clientCerPath, const char *pReq, char * &pResp)
    {
        std::ifstream certFile(clientCerPath);
        if (certFile.fail())
        {
            std::cout << "Open client sign cer fail! path:" << clientCerPath << std::endl;
            return FALSE;
        }

        std::stringstream certStream;
        certStream << certFile.rdbuf();
        std::string signCer = certStream.str();
        if (signCer.empty())
        {
            std::cout << "Read client sign cer fail! path:" << clientCerPath << std::endl;
            return FALSE;
        }

        BOOL isSuccess = VAuth_ParseAuthInfo(m_handle, authId.c_str(), signCer.c_str(), pReq, pResp);
        if (!isSuccess)
        {
            std::cout << "Parse auth fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }

        return TRUE;
    }

    BOOL ParseAuthError(const char *pReportError, char * &pError)
    {
        BOOL isSuccess = VAuth_ParseAuthError(m_handle, pReportError, pError);
        if (!isSuccess)
        {
            std::cout << "Parse auth error fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }

        return TRUE;
    }

    BOOL DecryptData(const unsigned char *pData, unsigned int dataLen, unsigned char * &pOutData, unsigned int &outLen)
    {
        BOOL isSuccess = VAuth_DecryptData(m_handle, TRUE, pData, dataLen, pOutData, outLen);
        if (!isSuccess)
        {
            std::cout << "Decrypt data fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }

        return TRUE;
    }

    BOOL DecryptPackData(const unsigned char *pData, unsigned int dataLen, unsigned char * &pOutData, unsigned int &outLen)
    {
        BOOL isSuccess = VAuth_DecryptPackData(m_handle, TRUE, pData, dataLen, pOutData, outLen);
        if (!isSuccess)
        {
            std::cout << "Decrypt pack data fail! err:" << VAuth_GetLastError() << std::endl;
            return FALSE;
        }

        {
            const std::string outFileName = "pack.out";
            std::ofstream outFile(outFileName, std::ios::binary | std::ios::trunc);
            if (!outFile)
            {
                std::cout << "Write file fail! path:" << outFileName << std::endl;
                return FALSE;
            }

            outFile.write((char *)pOutData, outLen);
        }

        return TRUE;
    }

    BOOL DecryptFileData(const std::string &filePath, unsigned char * &pOutData, unsigned int &outLen)
    {
        std::ifstream file(filePath, std::ios::binary | std::ios::ate);
        if (!file)
        {
            std::cout << "Read file fail! path:" << filePath << std::endl;
            return FALSE;
        }

        // 获取文件大小
        std::streamsize size = file.tellg();
        // 回到文件开头
        file.seekg(0, std::ios::beg);

        char *pFileData = new char[size];
        if (!pFileData)
        {
            std::cout << "Alloc mem fail! path:" << filePath << std::endl;
            return FALSE;
        }

        file.read(pFileData, size);

        BOOL isSuccess = FALSE;
        do
        {
            isSuccess = VAuth_DecryptFileData(m_handle, TRUE, (unsigned char *)pFileData, size, pOutData, outLen);
            if (!isSuccess)
            {
                std::cout << "Decrypt file data fail! err:" << VAuth_GetLastError() << std::endl;
                break;
            }

            isSuccess = TRUE;
        } while (false);

        if (pFileData)
            delete [] pFileData;

        {
            const std::string outFileName = "trans.mp4";
            std::ofstream outFile(outFileName, std::ios::binary | std::ios::trunc);
            if (!outFile)
            {
                std::cout << "Write file fail! path:" << outFileName << std::endl;
                return FALSE;
            }

            outFile.write((char *)pOutData, outLen);
        }

        return isSuccess;
    }

public:
    std::string m_id;
    std::string m_mode;
    std::string m_password;
    AuthDatas *m_pDatas;

    // ukey
    std::string m_ukInfo;

    int m_handle = -1;
};

int main()
{
    char *pReq = NULL;
    char *pResp = NULL;
    char *pFileData = NULL;

    do
    {
        const std::string clientCer = "client.cer";
        const std::string serverCer = "server.cer";
        std::string clientId = "44030900443338015001";
        std::string serverId = "44010000002000000001";
        const std::string testFileName = "file.data";
        const std::string testDeFileName = "trans.ps";
        bool isTestTrans = true;
        bool isTestPack = true;
        std::string mode = "ukey";

        AuthDatas datas;

		#ifdef _WIN32
		AuthClient client("", "88888888", "ukey");
        AuthServer server(serverId, "88888888", "ukey", &datas);
        isTestTrans = false;
        isTestPack = false;
		#else
		AuthClient client(clientId, "88888888", mode.c_str());
        AuthServer server(serverId, "88888888", mode.c_str(), &datas);
		#endif

		BOOL isSuccess = VAuth_Init();
		if (!isSuccess)
		{
            std::cout << "Init fail! err:" << VAuth_GetLastError() << std::endl;
            break;
		}

        if (!client.ListDev() || !client.Open())
            break;

        if (!server.ListDev() || !server.Open())
            break;

        if (clientId.empty())
            clientId = client.m_authId;

        if (serverId.empty())
            serverId = client.m_authId;

        if (!datas.Open(server.m_handle))
            break;

        if (!client.AuthReq(serverId, serverCer, pReq))
            break;

        std::cout << "Req: " << pReq << std::endl;

        if (!server.ParseAuthReq(clientId, pReq, pResp))
            break;

        std::cout << "Resp: " << pResp << std::endl;

        VAuth_Free(pReq);
        pReq = NULL;

        if (!client.Auth(pResp, pReq))
            break;

        std::cout << "Req: " << pReq << std::endl;

        VAuth_Free(pResp);
        pResp = NULL;

        if (!server.ParseAuth(clientId, clientCer, pReq, pResp))
            break;

        std::cout << "Resp: " << pResp << std::endl;

        VAuth_Free(pReq);
        pReq = NULL;

        if (!client.CheckResult(pResp, pReq))
        {
            VAuth_Free(pResp);
            pResp = NULL;

            server.ParseAuthError(pReq, pResp);
            std::cout << "Error: " << pResp << std::endl;

            break;
        }

        std::cout << "Reg success." << std::endl;

        VAuth_Free(pResp);
        pResp = NULL;
        VAuth_Free(pReq);
        pReq = NULL;

        // data
        if (!datas.AddCer(serverId, 1, serverCer))
            break;

        std::string testData = "<test>123456789</test>";
        unsigned int reqLen = 0;
        unsigned int respLen = 0;
        
        // test file
        if (isTestTrans)
        {
			if (!client.EncryptFileData(testFileName, (unsigned char *&)pReq, reqLen))
				break;

			if (!server.DecryptFileData(testDeFileName, (unsigned char *&)pResp, respLen))
				break;

            VAuth_Free(pResp);
            pResp = NULL;
            VAuth_Free(pReq);
            pReq = NULL;

            std::cout << "Crypt file pass." << std::endl;
        }

        // test pack
        if (isTestPack)
        {
			std::ifstream file(testFileName, std::ios::binary | std::ios::ate);
			if (!file)
			{
				std::cout << "Read pack file fail! path:" << testFileName << std::endl;
				break;
			}

			std::streamsize fileSize = file.tellg();
			file.seekg(0, std::ios::beg);
			pFileData = new char[fileSize];
			if (!pFileData)
			{
				std::cout << "Alloc mem fail! path:" << testFileName << std::endl;
				break;
			}

			file.read(pFileData, fileSize);
			file.close();

			if (!client.EncryptPackData((unsigned char *)pFileData, (unsigned int)fileSize, (unsigned char *&)pReq, reqLen))
				break;

			if (!server.DecryptPackData((unsigned char *)pReq, (unsigned int)reqLen, (unsigned char *&)pResp, respLen))
				break;

			if (fileSize != respLen || memcmp(pFileData, pResp, fileSize) != 0)
			{
				std::cout << "Pack diff. enSize: " << reqLen << " deSize: " << respLen << std::endl;
				break;
			}

			std::cout << "Crypt pack pass." << std::endl;

			VAuth_Free(pResp);
			pResp = NULL;
			VAuth_Free(pReq);
			pReq = NULL;
        }


        // test data
        if (!client.EncryptData((unsigned char *)testData.c_str(), (unsigned int)testData.size(), (unsigned char * &)pReq, reqLen))
            break;

        if (!server.DecryptData((unsigned char *)pReq, (unsigned int)reqLen, (unsigned char * &)pResp, respLen))
            break;

        if (testData.size() != respLen || memcmp(testData.c_str(), pResp, testData.size()) != 0)
        {
            std::cout << "Text diff. enSize: " << reqLen << " deSize: " << respLen << std::endl;
            break;
        }

        std::cout << "Crypt text pass. data: " << testData  << " enSize: " << reqLen << " deSize : " << respLen << std::endl;

        VAuth_Free(pResp);
        pResp = NULL;
        VAuth_Free(pReq);
        pReq = NULL;

        const unsigned int cryptLen = 256;
        unsigned char cryptData[cryptLen];
        for (unsigned int i = 0; i < cryptLen; ++i)
            cryptData[i] = (unsigned char)i;

        reqLen = 0;
        if (!client.EncryptData((unsigned char *)cryptData, (unsigned int)cryptLen, (unsigned char * &)pReq, reqLen))
            break;

        respLen = 0;
        if (!server.DecryptData((unsigned char *)pReq, (unsigned int)reqLen, (unsigned char * &)pResp, respLen))
            break;

        if (cryptLen != respLen || memcmp(cryptData, pResp, cryptLen) != 0)
        {
            std::cout << "Data diff. enSize: " << reqLen << " deSize: " << respLen << std::endl;
            break;
        }

        std::cout << "Crypt data pass. enSize: " << reqLen << " deSize: " << respLen << std::endl;

        // verify
        VAuth_Free(pResp);
        pResp = NULL;

        pReq[reqLen - 3] += 1;

        respLen = 0;
        if (server.DecryptData((unsigned char *)pReq, (unsigned int)reqLen, (unsigned char *&)pResp, respLen))
        {
            std::cout << "Verify test fail! enSize: " << reqLen << " deSize: " << respLen << std::endl;
            break;
        }

        std::cout << "Verify pass. enSize: " << reqLen << " deSize: " << respLen << std::endl;

        VAuth_Free(pReq);
        pReq = NULL;
        
        std::cout << "Pass. Wait key cleanup.\n";
        std::cin.get();

    } while (false);

    if (pReq)
        VAuth_Free(pReq);
    if (pResp)
        VAuth_Free(pResp);
    if (pFileData)
        delete pFileData;

    VAuth_Cleanup();
    std::cout << "End. Wait key exit.\n";
    std::cin.get();
}

