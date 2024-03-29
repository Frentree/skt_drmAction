package com.iopts.scheduler;

import org.json.JSONArray;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibatis.sqlmap.client.SqlMapClient;
import com.skyun.app.util.config.AppConfig;
import com.skyun.app.util.config.IoptsCurl;
import com.skyun.recon.util.database.ibatis.SqlMapInstance;
import com.skyun.recon.util.database.ibatis.tr.DBInsertTable;
import com.skyun.recon.util.database.ibatis.vo.targetVo;

public class SetDuration {

	private static Logger logger = LoggerFactory.getLogger(SetDuration.class);
	
	DBInsertTable tr = new DBInsertTable();
	private static SqlMapClient sqlMap = null;
	
	private String gid = "";
	private String tid = "";
	
	private double duration = -1;
	private double all_duration = 0.0;
	private String all_scan_date = "";
	
//	private static int ap_no = 0;
	
	public static String user = AppConfig.getProperty("config.recon.user");
	public static String pass = AppConfig.getProperty("config.recon.pawwsord");
	public static String ip = "";
	public static String port = AppConfig.getProperty("config.recon.port");
	public static String api_ver = AppConfig.getProperty("config.recon.api.version");
	
	public SetDuration(targetVo info) {
		
		logger.info("setDuration");
		this.sqlMap = SqlMapInstance.getSqlMapInstance();
		
		this.gid = info.getGroup_id();
		this.tid = info.getTarget_id();
		
		int ap_no = info.getAp_no();
		
		this.user = (ap_no == 0) ? AppConfig.getProperty("config.recon.user") : AppConfig.getProperty("config.recon.user_"+(ap_no+1));
		this.pass = (ap_no == 0) ? AppConfig.getProperty("config.recon.pawwsord") : AppConfig.getProperty("config.recon.pawwsord_"+(ap_no+1));
		this.ip = (ap_no == 0) ? AppConfig.getProperty("config.recon.ip") : AppConfig.getProperty("config.recon.ip_"+(ap_no+1)) ;
		
		String curlurl = String.format("-k -X GET -u %s:%s https://%s:%s/beta/targets/%s/consolidated", this.user, pass,
				this.ip, this.port, info.getTarget_id());
		
		logger.info("curlurl [" + curlurl + "]");

		String[] array = curlurl.split(" ");
		
		String json_string;
		try {

			json_string = new IoptsCurl().opt(array).exec(null);

			if (json_string == null || json_string.length() < 1 || json_string.contains("Resource not found.")) {
				logger.error("Data Null Check IP or ID: " + curlurl);
			} else {
				
				logger.info("json_string :: " + json_string);
				JSONArray temp1 = new JSONArray(json_string);

				for (int i = 0; i <= temp1.length()/10; i++) {
					getConsolidatedData(temp1.get(i*10).toString());
					if(((i+1)*10) >= temp1.length()) {
						break;
					}
					if(this.all_duration > 0 && this.duration >= 0) {
						break;
					}
				}
				
				if(this.all_duration > 0) {
					logger.info(String.valueOf(Math.round(this.all_duration)));
					logger.info(this.all_scan_date);
					info.setAll_duration(String.valueOf(Math.round(this.all_duration)));
					info.setAll_scan_date(this.all_scan_date);
				}
				if(this.duration < 0) {					
					this.duration = 0;
				}
				info.setDuration(String.valueOf(Math.round(this.duration)));
				tr.setDBInsertTable("update.setTargetDuration", info);

			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	private void getConsolidatedData(String timestamp) {
		
		String curlurl = String.format("-k -X GET -u %s:%s https://%s:%s/beta/targets/%s/consolidated/%s", user, pass, 
				AppConfig.getProperty("config.recon.ip"), AppConfig.getProperty("config.recon.port"), tid, timestamp);
		
		logger.info("curlurl [" + curlurl + "]");
		
		String[] array = curlurl.split(" ");
		
		String json_string;
		try {

			json_string = new IoptsCurl().opt(array).header("Accept: text/html").exec(null);
			

			Document doc = Jsoup.parse(json_string);
			
			
			Elements tr = doc.select(".ul");
			for(int i=0; i<tr.size(); i++) {
				Double duration = 0.0;
				Elements td = tr.get(i).select("td");
				if(10 == td.size()) {
					
					String td_duration = td.get(2).text();
					
					if("".equals(td.get(2).text())) {
						td_duration = "0";
					}
					// duration 체크
					if (td_duration.contains("day")) {
						td_duration = td_duration.replaceAll(" days", "");
						td_duration = td_duration.replaceAll(" day", "");
						duration = Double.parseDouble(td_duration)*24*3600;
					} else if(td_duration.contains("hour")) {
						td_duration = td_duration.replaceAll(" hours", "");
						td_duration = td_duration.replaceAll(" hour", "");
						duration = Double.parseDouble(td_duration)*3600;
					} else if(td_duration.contains("min")) {
						td_duration = td_duration.replaceAll(" mins", "");
						td_duration = td_duration.replaceAll(" min", "");
						duration = Double.parseDouble(td_duration)*60;
					} else if(td_duration.contains("second")) {
						td_duration = td_duration.replaceAll(" seconds", "");
						td_duration = td_duration.replaceAll(" second", "");
						duration = Double.parseDouble(td_duration);
					}
					
					logger.info("["+i+"]"+td.get(0).text() + " :: " + this.duration + " // " + duration);
					// duration 셋팅
					if(td.get(0).text().contains("All local files")) {
						if(this.all_duration < duration) {
							this.all_duration = duration;
							this.all_scan_date = setScanDate(td.get(1).text());
						}
					} else {
						if(this.duration < 0) {
							logger.info("set duration :: " + this.duration + " // " + duration);
							this.duration = duration;
						}
					}						
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String setScanDate(String text) {
		String[] temp = text.split(" ");
		String[] date = temp[0].split("-");
		
		if("Jan".equals(date[1])) {
			date[1] = "01";
		}else if("Feb".equals(date[1])) {
			date[1] = "02";
		}else if("Mar".equals(date[1])) {
			date[1] = "03";
		}else if("Apr".equals(date[1])) {
			date[1] = "04";
		}else if("May".equals(date[1])) {
			date[1] = "05";
		}else if("Jun".equals(date[1])) {
			date[1] = "06";
		}else if("Jul".equals(date[1])) {
			date[1] = "07";
		}else if("Aug".equals(date[1])) {
			date[1] = "08";
		}else if("Sep".equals(date[1])) {
			date[1] = "09";
		}else if("Oct".equals(date[1])) {
			date[1] = "10";
		}else if("Nov".equals(date[1])) {
			date[1] = "11";
		}else if("Dec".equals(date[1])) {
			date[1] = "12";
		}
		
		return date[2]+"-"+date[1]+"-"+date[0]+" "+temp[1];
	}

}
