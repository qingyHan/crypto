package com.crypto.api.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import javax.sql.DataSource;
import java.util.Objects;

/**
 * MySQL数据源配置
 * <p>
 * 配置主数据源，使用Druid连接池和MyBatis Plus
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "spring.datasource")
@MapperScan(basePackages = "com.crypto.api.repository", sqlSessionFactoryRef = "mysqlSqlSessionFactory")
public class MysqlDataSourceConfig {

    private String url = "jdbc:mysql://localhost:3306/crypto_analysis";
    private String username = "root";
    private String password = "";
    private String driverClassName = "com.mysql.cj.jdbc.Driver";

    /**
     * 配置MySQL数据源
     * <p>
     * 使用Druid作为连接池，配置连接池参数和验证机制
     *
     * @return MySQL数据源实例
     */
    @Bean(name = "mysqlDataSource")
    @Primary // 设置为主数据源
    public DataSource mysqlDataSource() {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);

        // 连接池配置
        dataSource.setInitialSize(5);
        dataSource.setMinIdle(5);
        dataSource.setMaxActive(20);
        dataSource.setMaxWait(60000);

        // 验证配置
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setTestWhileIdle(true);
        dataSource.setTestOnBorrow(false);
        dataSource.setTestOnReturn(false);

        log.info("MySQL数据源配置完成：{}", url);
        return dataSource;
    }

    /**
     * 配置MySQL SqlSessionFactory（配置MyBatis Plus）
     * <p>
     * 设置Mapper XML文件位置和实体类别名包，配置MyBatis Plus相关参数
     *
     * @param mysqlDataSource MySQL数据源
     * @return SqlSessionFactory实例
     * @throws Exception 配置异常
     */
    @Bean(name = "mysqlSqlSessionFactory")
    @Primary
    public SqlSessionFactory mysqlSqlSessionFactory(DataSource mysqlDataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(mysqlDataSource);
        // 设置Mapper XML文件位置：扫描classpath下所有mapper目录的XML文件
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:/mapper/**/*.xml"));
        
        // 设置实体类别名包：在Mapper XML中可以直接使用类名，不需要写全限定名
        factory.setTypeAliasesPackage("com.crypto.common.entity,com.crypto.api.entity");

        // MyBatis Plus配置
        MybatisConfiguration configuration = new MybatisConfiguration();
        
        // 下划线转驼峰：数据库字段 user_name → Java属性 userName
        configuration.setMapUnderscoreToCamelCase(true);
        
        // 禁用缓存：每次查询都从数据库读取最新数据，避免缓存不一致问题
        configuration.setCacheEnabled(false);
        
        factory.setConfiguration(configuration);

        log.info("MySQL SqlSessionFactory配置完成（使用MyBatis Plus）");
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        return Objects.requireNonNull(sqlSessionFactory, "SqlSessionFactory creation failed");
    }

    /**
     * 配置MySQL事务管理器
     * <p>
     * 创建DataSourceTransactionManager实例，用于管理MySQL数据库事务。
     * 支持@Transactional注解，保证多个数据库操作要么全部成功，要么全部失败。
     *
     * @param mysqlDataSource MySQL数据源
     * @return 事务管理器实例
     */
    @Bean(name = "mysqlTransactionManager")
    @Primary
    public DataSourceTransactionManager mysqlTransactionManager(DataSource mysqlDataSource) {
        return new DataSourceTransactionManager(mysqlDataSource);
    }

    /**
     * 配置MySQL SqlSessionTemplate
     * <p>
     * 创建SqlSessionTemplate实例，用于执行MyBatis操作。
     * 通常通过Mapper接口使用，不需要直接使用SqlSessionTemplate。
     *
     * @param mysqlSqlSessionFactory SqlSessionFactory实例
     * @return SqlSessionTemplate实例
     */
    @Bean(name = "mysqlSqlSessionTemplate")
    @Primary
    public SqlSessionTemplate mysqlSqlSessionTemplate(SqlSessionFactory mysqlSqlSessionFactory) {
        return new SqlSessionTemplate(mysqlSqlSessionFactory);
    }
}
