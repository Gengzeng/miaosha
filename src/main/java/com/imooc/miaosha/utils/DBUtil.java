package com.imooc.miaosha.utils;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;


@Configuration
public class DBUtil {




	public static Connection getConn() throws Exception{
		//System.out.println(url);
	    String username = "root";
		String password = "123";
		String driver = "com.mysql.cj.jdbc.Driver";
		String url =   "jdbc:mysql://localhost:3306/miaosha?useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&useSSL=false&serverTimezone=GMT%2B8&amp";


		Class.forName(driver);
		return DriverManager.getConnection(url,username,password);

	}
}
