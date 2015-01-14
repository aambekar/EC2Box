/**
 * Copyright 2013 Sean Kavanagh - sean.p.kavanagh6@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ec2box.manage.action;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.interceptor.ServletResponseAware;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairResult;
import com.amazonaws.services.ec2.model.DescribeKeyPairsRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.ec2box.common.util.AppConfig;
import com.ec2box.ias.IasClient;
import com.ec2box.ias.model.AwsSshKey;
import com.ec2box.ias.model.IasRequestHeader;
import com.ec2box.manage.db.AWSCredDB;
import com.ec2box.manage.db.EC2KeyDB;
import com.ec2box.manage.model.AWSCred;
import com.ec2box.manage.model.EC2Key;
import com.ec2box.manage.model.SortedSet;
import com.ec2box.manage.util.AWSClientConfig;
import com.google.gson.Gson;
import com.opensymphony.xwork2.ActionSupport;
import com.sun.jersey.api.client.WebResource;

/**
 * Action to import private key for EC2 instances
 */
public class EC2KeyAction extends ActionSupport implements ServletResponseAware {

	EC2Key ec2Key;
	IasRequestHeader header;
	SortedSet sortedSet = new SortedSet();
	HttpServletResponse servletResponse;
	static Map<String, String> ec2RegionMap = AppConfig.getMapProperties("ec2Regions");
	List<AWSCred> awsCredList = AWSCredDB.getAWSCredList();

	@Action(value = "/manage/viewEC2Keys", results = { @Result(name = "success", location = "/manage/view_ec2_keys.jsp") })
	public String viewEC2Keys() {

		sortedSet = EC2KeyDB.getEC2KeySet(sortedSet);

		return SUCCESS;

	}

	/**
	 * returns keypairs as a json string
	 */
	@Action(value = "/manage/getKeyPairJSON")
	public String getKeyPairJSON() {

		AWSCred awsCred = AWSCredDB.getAWSCred(ec2Key.getAwsCredId());

		// set AWS credentials for service
		BasicAWSCredentials awsCredentials = new BasicAWSCredentials(awsCred.getAccessKey(), awsCred.getSecretKey());
		AmazonEC2 service = new AmazonEC2Client(awsCredentials, AWSClientConfig.getClientConfig());

		service.setRegion(Region.getRegion(Regions.fromName(ec2Key.getEc2Region())));

		DescribeKeyPairsRequest describeKeyPairsRequest = new DescribeKeyPairsRequest();

		DescribeKeyPairsResult describeKeyPairsResult = service.describeKeyPairs(describeKeyPairsRequest);

		List<KeyPairInfo> keyPairInfoList = describeKeyPairsResult.getKeyPairs();
		String json = new Gson().toJson(keyPairInfoList);
		try {
			servletResponse.getOutputStream().write(json.getBytes());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	@Action(value = "/manage/submitEC2Key", results = { @Result(name = "input", location = "/manage/view_ec2_keys.jsp"),
			@Result(name = "success", location = "/manage/viewEC2Keys.action", type = "redirect") })
	public String submitEC2Key() {

		String retVal = SUCCESS;

		try {

			// get AWS credentials from DB
			AWSCred awsCred = AWSCredDB.getAWSCred(ec2Key.getAwsCredId());

			// set AWS credentials for service
			BasicAWSCredentials awsCredentials = new BasicAWSCredentials(awsCred.getAccessKey(), awsCred.getSecretKey());

			// create service
			AmazonEC2 service = new AmazonEC2Client(awsCredentials, AWSClientConfig.getClientConfig());
			service.setRegion(Region.getRegion(Regions.fromName(ec2Key.getEc2Region())));

			// create key pair request
			CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest();
			createKeyPairRequest.withKeyName(ec2Key.getKeyNm());

			// call service
			CreateKeyPairResult createKeyPairResult = service.createKeyPair(createKeyPairRequest);
			// get key pair result
			KeyPair keyPair = createKeyPairResult.getKeyPair();

			// set private key
			String privateKey = keyPair.getKeyMaterial();
			ec2Key.setPrivateKey(privateKey);

			// add to db
			EC2KeyDB.saveEC2Key(ec2Key);

		} catch (AmazonServiceException ex) {
			addActionError(ex.getMessage());
			retVal = INPUT;
		}

		return retVal;

	}

	@Action(value = "/manage/importEC2Key", results = { @Result(name = "input", location = "/manage/view_ec2_keys.jsp"),
			@Result(name = "success", location = "/manage/viewEC2Keys.action", type = "redirect") })
	public String importEC2Key() {

		String retVal = SUCCESS;

		try {
			// get AWS credentials from DB
			AWSCred awsCred = AWSCredDB.getAWSCred(ec2Key.getAwsCredId());

			// set AWS credentials for service
			BasicAWSCredentials awsCredentials = new BasicAWSCredentials(awsCred.getAccessKey(), awsCred.getSecretKey());

			// create service
			AmazonEC2 service = new AmazonEC2Client(awsCredentials, AWSClientConfig.getClientConfig());
			service.setRegion(Region.getRegion(Regions.fromName(ec2Key.getEc2Region())));

			// describe key pair request
			DescribeKeyPairsRequest describeKeyPairsRequest = new DescribeKeyPairsRequest();
			describeKeyPairsRequest.setKeyNames(Arrays.asList(ec2Key.getKeyNm()));

			// call service
			DescribeKeyPairsResult describeKeyPairsResult = service.describeKeyPairs(describeKeyPairsRequest);

			if (describeKeyPairsResult != null && describeKeyPairsResult.getKeyPairs().size() > 0) {
				// add to db
				EC2KeyDB.saveEC2Key(ec2Key);
			} else {
				addActionError("Imported key does not exist on AWS");
				retVal = INPUT;
			}

		} catch (AmazonServiceException ex) {
			addActionError(ex.getMessage());
			retVal = INPUT;

		}

		return retVal;

	}

	@Action(value = "/manage/importEC2KeysFromIAS", results = { @Result(name = "input", location = "/manage/view_ec2_keys.jsp"),
			@Result(name = "success", location = "/manage/viewEC2Keys.action", type = "redirect") })
	public String importEC2KeysFromIAS() {
		String retVal = SUCCESS;
		try {

			WebResource client = IasClient.getClient(AppConfig.getProperty("iasServer"));
			String jsonData = client.path("/IAS/aws/sshkey/keys").queryParam("region", ec2Key.getEc2Region()).accept(MediaType.APPLICATION_JSON)
					.header("crowd-sso-name", header.getUsername()).header("crowd-sso-token", header.getToken()).get(String.class);
			if (!jsonData.isEmpty()) {
				AwsSshKey[] keys = new Gson().fromJson(jsonData, AwsSshKey[].class);
				for (AwsSshKey key : keys) {
					EC2Key ec2Key = new EC2Key();
					ec2Key.setAwsCredId(this.ec2Key.getAwsCredId());
					ec2Key.setAccessKey(this.ec2Key.getAccessKey());
					ec2Key.setEc2Region(key.getRegion());
					ec2Key.setKeyNm(key.getKeyName());
					ec2Key.setPrivateKey(key.getSshKey());
					EC2KeyDB.saveEC2Key(ec2Key);
				}
			}
		} catch (Exception e) {
			addActionError(e.getMessage());
			retVal = INPUT;
		}

		return retVal;
	}

	@Action(value = "/manage/deleteEC2Key", results = { @Result(name = "success", location = "/manage/viewEC2Keys.action", type = "redirect") })
	public String deleteEC2Key() {

		EC2KeyDB.deleteEC2Key(ec2Key.getId());

		return SUCCESS;

	}

	/**
	 * Validates fields for importing an ec2 key
	 */
	public void validateImportEC2Key() {

		if (ec2Key.getAwsCredId() == null) {
			addFieldError("ec2Key.awsCredId", "Required");
		}
		if (ec2Key.getEc2Region() == null || ec2Key.getEc2Region().trim().equals("")) {
			addFieldError("ec2Key.ec2Region", "Required");
		}
		if (ec2Key.getKeyNm() == null || ec2Key.getKeyNm().trim().equals("")) {
			addFieldError("ec2Key.keyNm", "Required");
		}
		if (ec2Key.getPrivateKey() == null || ec2Key.getPrivateKey().trim().equals("")) {
			addFieldError("ec2Key.privateKey", "Required");
		}
		if (hasErrors()) {

			sortedSet = EC2KeyDB.getEC2KeySet(sortedSet);
		}
	}

	/**
	 * Validates fields for credential submit
	 */
	public void validateSubmitEC2Key() {
		if (ec2Key.getEc2Region() == null || ec2Key.getEc2Region().trim().equals("")) {
			addFieldError("ec2Key.ec2Region", "Required");
		}
		if (ec2Key.getKeyNm() == null || ec2Key.getKeyNm().trim().equals("")) {
			addFieldError("ec2Key.keyNm", "Required");
		}
		if (hasErrors()) {

			sortedSet = EC2KeyDB.getEC2KeySet(sortedSet);
		}

	}

	public SortedSet getSortedSet() {
		return sortedSet;
	}

	public void setSortedSet(SortedSet sortedSet) {
		this.sortedSet = sortedSet;
	}

	public Map getEc2RegionMap() {
		return ec2RegionMap;
	}

	public void setEc2RegionMap(Map<String, String> ec2RegionMap) {
		this.ec2RegionMap = ec2RegionMap;
	}

	public EC2Key getEc2Key() {
		return ec2Key;
	}

	public void setEc2Key(EC2Key ec2Key) {
		this.ec2Key = ec2Key;
	}

	public HttpServletResponse getServletResponse() {
		return servletResponse;
	}

	public void setServletResponse(HttpServletResponse servletResponse) {
		this.servletResponse = servletResponse;
	}

	public List<AWSCred> getAwsCredList() {
		return awsCredList;
	}

	public void setAwsCredList(List<AWSCred> awsCredList) {
		this.awsCredList = awsCredList;
	}

	public IasRequestHeader getHeader() {
		return header;
	}

	public void setHeader(IasRequestHeader header) {
		this.header = header;
	}

}
