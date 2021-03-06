package org.jigsaw.payment.core.mysql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

import org.jigsaw.payment.core.PaymentSharder;
import org.jigsaw.payment.mysql.JdbcProtobufTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.protobuf.Message;

/**
 * 增加表可以减少锁的争用，增加库可以增加并发性能。
 * 按照这个原则，分表分库按照用户id（长整形）来进行，uid最后一位用来做分表。分库按照2、4、8的级数来扩展： 表号是： uid对10取模，即最后一位；
 * 库号是： uid/10 后， 按照库的数量来取模。 加库时，先将表数据同步到1个从库，同步完成后，从库升级为主库即可。 表里有冗余数据，但是不会丢数据。
 * 
 * @author shamphone@gmail.com
 * @version 1.0.0
 * @date 2017年8月10日
 */
public class MySQLPaymentSharder<T extends Message> implements PaymentSharder<T> {
	private List<JdbcProtobufTemplate<Long, T>> templates;
	private int tableCount = 10;
	private int databaseCount = 2;
	private String tableNamePattern = "pay_order_%01d";
	private Class<T> messageClass;
	
	public MySQLPaymentSharder(Class<T> messageClass){
		this.messageClass = messageClass;
	}
	/**
	 * 初始化template列表；
	 * 
	 * @param dataSourceList
	 */
	public void setDataSourceList(List<DataSource> dataSourceList) {
		this.templates = Collections
				.synchronizedList(new ArrayList<JdbcProtobufTemplate<Long, T>>(
						dataSourceList.size()));
		for (DataSource dataSource : dataSourceList) {
			JdbcTemplate template = new JdbcTemplate(dataSource);
			JdbcProtobufTemplate<Long, T> wrapper = new JdbcProtobufTemplate<Long, T>(
					template,messageClass);
			this.templates.add(wrapper);
		}
		if (this.templates.size() != this.databaseCount)
			throw new IllegalArgumentException(
					"please check your database configuration, it should have "
							+ databaseCount + " count.");
	}
	

	public void setTableCount(int tableCount) {
		this.tableCount = tableCount;
	}

	public void setDatabaseCount(int databaseCount) {
		this.databaseCount = databaseCount;
	}

	public void setTableNamePattern(String tableNamePattern) {
		this.tableNamePattern = tableNamePattern;
	}

	@Override
	public JdbcProtobufTemplate<Long, T> getTemplateByUserId(long uid) {
		return this.templates.get((int) (uid / tableCount % databaseCount));
	}

	@Override
	public String getTableByUserId(long uid) {
		return String.format(tableNamePattern, uid % this.tableCount);
	}

	@Override
	public JdbcProtobufTemplate<Long, T> getTemplateById(long payCode) {
		return this.templates
				.get((int) (payCode / 10 / tableCount % databaseCount));
	}

	@Override
	public String getTableById(long payCode) {
		return String.format(tableNamePattern, payCode / 10 % tableCount);
	}

}
