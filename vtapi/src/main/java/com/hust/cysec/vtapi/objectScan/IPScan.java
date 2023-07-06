package com.hust.cysec.vtapi.objectScan;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.*;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellUtil;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.json.JSONException;
import org.json.JSONObject;

public class IPScan extends Scan {
	//IP Address validation
	private static final String IP_ADDRESS_PATTERN = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"; 
	private static final Pattern pattern = Pattern.compile(IP_ADDRESS_PATTERN);
	
	@Override
	public boolean isValid() {
		Matcher matcher = pattern.matcher(getName());
		if (matcher.matches()) {
			setObjectId(getName());
			return true;
		}
		else
			setName(null);
		return false;
	}
	
	@Override
	public void getReport(String apikey) throws IOException, InterruptedException {
		if (getObjectId() == null)
			return;
		
		//GET REPORT req
		HttpRequest request = HttpRequest.newBuilder()
			    .uri(URI.create("https://www.virustotal.com/api/v3/ip_addresses/" + getObjectId()))
			    .header("accept", "application/json")
			    .header("x-apikey", apikey)
			    .method("GET", HttpRequest.BodyPublishers.noBody())
			    .build();
			HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
			
		JSONObject json = new JSONObject(response.body());
		setJson(json);
		
		//SET ATTRIBUTES
		try {
			//GET BASIC INFO
			setName(json.getJSONObject("data").getJSONObject("attributes").getString("network"));
			setObjectId(json.getJSONObject("data").getString("id"));
			
			//GET ANALYSIS
			setTime(json.getJSONObject("data").getJSONObject("attributes").getInt("last_analysis_date"));
			setHarmless(json.getJSONObject("data").getJSONObject("attributes").getJSONObject("last_analysis_stats").getInt("harmless"));
			setUndetected(json.getJSONObject("data").getJSONObject("attributes").getJSONObject("last_analysis_stats").getInt("undetected"));
			setMalicious(json.getJSONObject("data").getJSONObject("attributes").getJSONObject("last_analysis_stats").getInt("malicious"));
			setSuspicious(json.getJSONObject("data").getJSONObject("attributes").getJSONObject("last_analysis_stats").getInt("suspicious"));
			setTimeout(json.getJSONObject("data").getJSONObject("attributes").getJSONObject("last_analysis_stats").getInt("timeout"));
		} catch (Exception e) {
			try {
		        System.out.println("ERROR: " + json.getJSONObject("error").getString("message") + " (" + json.getJSONObject("error").getString("code") + ")");
			} catch (Exception ee) {
				//check if analysis not finished
				if (e.getMessage().equals("JSONObject[\"last_analysis_date\"] not found."))
					System.out.println("WARNING: No finished analysis found!");
				else
					System.out.println("ERROR: " + e.getMessage());
			}
	    }
	}
	
	@Override
	public void writeExcel(XSSFSheet sheet) {
		if (sheet == null) {
			System.out.println("ERROR: Can't write anything.");
			return;
		}

	//WRITE BASIC INFO
		Row row = sheet.getRow(1);
        CellUtil.getCell(row, 0).setCellValue("type");
        CellUtil.getCell(row, 1).setCellValue("id");
        CellUtil.getCell(row, 2).setCellValue("name");
        CellUtil.getCell(row, 9).setCellValue("undetected");
        CellUtil.getCell(row, 10).setCellValue("harmless");
        CellUtil.getCell(row, 11).setCellValue("suspicious");
        CellUtil.getCell(row, 12).setCellValue("malicious");
        CellUtil.getCell(row, 13).setCellValue("timeout");
        CellUtil.getCell(row, 15).setCellValue("last_analysis_date");

        row = sheet.getRow(2);
        CellUtil.getCell(row, 0).setCellValue("ip");
        CellUtil.getCell(row, 1).setCellValue(getObjectId());
        CellUtil.getCell(row, 2).setCellValue(getName());
        CellUtil.getCell(row, 9).setCellValue(getUndetected());
        CellUtil.getCell(row, 10).setCellValue(getHarmless());
        CellUtil.getCell(row, 11).setCellValue(getSuspicious());
        CellUtil.getCell(row, 12).setCellValue(getMalicious());
        CellUtil.getCell(row, 13).setCellValue(getTimeout());
        CellUtil.getCell(row, 15).setCellValue(getTime());

   //WRITE ANALYSIS RESULTS
        row = sheet.getRow(1);
        CellUtil.getCell(row, 16).setCellValue("engine_name");
        CellUtil.getCell(row, 17).setCellValue("category");
        CellUtil.getCell(row, 18).setCellValue("result");

        List<JSONObject> engines = new ArrayList<>();
        JSONObject json = getJson().getJSONObject("data").getJSONObject("attributes").getJSONObject("last_analysis_results");
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            JSONObject nestedJsonObject = json.getJSONObject(keys.next());
            engines.add(nestedJsonObject);
        }
        Collections.sort(engines, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject j1, JSONObject j2) {
                String name1 = (String) j1.get("engine_name");
                String name2 = (String) j2.get("engine_name");
                return name1.compareToIgnoreCase(name2);
            }
        });

        int i_row = 2;
        for (JSONObject engine: engines) {
        	row = sheet.getRow(i_row);
        	if (row == null)
        		row = sheet.createRow(i_row);
        	CellUtil.getCell(row, 16).setCellValue(engine.getString("engine_name"));
            CellUtil.getCell(row, 17).setCellValue(engine.getString("category"));
            try {
            	CellUtil.getCell(row, 18).setCellValue(engine.getString("result"));
            } catch (JSONException e) {}
        	i_row++;
        }
        if (i_row < 101) {
        	row = sheet.getRow(101);
        	CellUtil.getCell(row, 16).setBlank();
        }

    // WRITE OTHER IP INFOS
        row = sheet.getRow(1);
        CellUtil.getCell(row, 4).setCellValue("whois_date");
        CellUtil.getCell(row, 5).setCellValue("country");
        CellUtil.getCell(row, 6).setCellValue("as_owner");
        CellUtil.getCell(row, 7).setCellValue("asn");
        CellUtil.getCell(row, 19).setCellValue("reputation");
        CellUtil.getCell(row, 20).setCellValue("harmless");
        CellUtil.getCell(row, 21).setCellValue("malicious");

        json = getJson().getJSONObject("data").getJSONObject("attributes");
        row = sheet.getRow(2);
        CellUtil.getCell(row, 4).setCellValue(json.getLong("whois_date"));
        try {CellUtil.getCell(row, 5).setCellValue(json.getString("country"));}
            catch (Exception e) {CellUtil.getCell(row, 5).setCellValue("Unknown");}
        CellUtil.getCell(row, 6).setCellValue(json.getString("as_owner"));
        CellUtil.getCell(row, 7).setCellValue(json.getInt("asn"));
        CellUtil.getCell(row, 19).setCellValue(json.getInt("reputation"));
        CellUtil.getCell(row, 20).setCellValue(json.getJSONObject("total_votes").getInt("harmless"));
        CellUtil.getCell(row, 21).setCellValue(json.getJSONObject("total_votes").getInt("malicious"));


    // WRITE WHOIS
        row = sheet.getRow(1);
        CellUtil.getCell(row, 22).setCellValue("whois");
        String whois = getJson().getJSONObject("data").getJSONObject("attributes").getString("whois");
        i_row = 2;
        System.out.println(sheet.getLastRowNum());
        for (String line : whois.split("\n")) {
        	String[] info = line.split(": ");
        	row = sheet.getRow(i_row);
            if (row==null)
                row = sheet.createRow(i_row);
        	CellUtil.getCell(row, 22).setCellValue(info[0]);
            if (info.length >= 2) {
            	CellUtil.getCell(row, 23).setCellValue(info[1]);
            }
            i_row++;
        }
	}
}
