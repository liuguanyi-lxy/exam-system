package com.atguigu.exam.service.impl;

import com.atguigu.exam.config.properties.MinioProperties;
import com.atguigu.exam.service.FileUploadService;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * 文件上传服务实现类
 * 基于MinIO对象存储服务实现文件上传功能，支持轮播图、视频等各类文件的上传和管理
 * 自动处理桶创建、访问权限配置、文件名唯一性保证等核心逻辑
 *
 * @author 赵伟风
 * @version 1.0
 */
@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private MinioProperties minioProperties;

    /**
     * 上传文件到MinIO对象存储
     * 自动检查并创建存储桶，设置公共读权限，生成唯一的对象名称以避免文件覆盖
     *
     * @param folder 文件夹名称，用于区分不同类型的文件（如banner、video等）
     * @param file 要上传的文件对象，包含文件内容和元数据
     * @return 文件的完整访问URL，格式为：端点/桶名/文件夹/日期/UUID_原文件名
     * @throws Exception 文件上传过程中可能出现的异常，如IO异常、MinIO连接异常等
     */
    @Override
    public String uploadFile(String folder, MultipartFile file) throws Exception {
        // 检查存储桶是否存在，不存在则创建并设置公共读访问策略
        boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucketName()).build());
        //2. 不存在，创建桶，同时设置访问权限
        if (!bucketExists) {
            //创建桶
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucketName()).build());
            //通过formatted方法将minioProperties中的桶名动态填入资源路径，生成针对特定存储桶的公共读权限配置字符串，用于后续设置桶策略
            String config = """
                        {
                              "Statement" : [ {
                                "Action" : "s3:GetObject",
                                "Effect" : "Allow",
                                "Principal" : "*",
                                "Resource" : "arn:aws:s3:::%s/*"
                              } ],
                              "Version" : "2012-10-17"
                        }
                    """.formatted(minioProperties.getBucketName());//桶名替代资源访问路径的%s参数
            //给指定的桶设置访问权限
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .config(config)
                    .build());
        }
        
        // 生成唯一的对象名称：文件夹/日期/UUID_原文件名，避免文件覆盖
        String objectName = folder + "/" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + "/" +
                UUID.randomUUID().toString().replaceAll("-","")+"_"+ file.getOriginalFilename();

        log.debug("文件上传核心业务方法，处理后的文件对象名：{}",objectName);

        // 执行文件上传，使用流式上传方式，由MinIO自动处理分片
        minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .contentType(file.getContentType())
                        .object(objectName)//桶中对象名
                        .stream(file.getInputStream(),file.getSize(),-1)
                .build());

        // 拼接完整的文件访问URL并返回
        String url = String.join("/", minioProperties.getEndpoint(), minioProperties.getBucketName(), objectName);
        log.info("文件上传核心业务，完成{}文件上传，返回地址为：{}",objectName,url);
        return url;
    }
}
