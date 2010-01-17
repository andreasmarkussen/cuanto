/*
 Copyright (c) 2008 thePlatform, Inc.

This file is part of Cuanto, a test results repository and analysis program.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/


package cuanto.api

import com.thoughtworks.xstream.XStream
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.HttpMethod
import org.apache.commons.httpclient.HttpStatus
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.StringRequestEntity
import org.apache.commons.httpclient.methods.multipart.FilePart
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity
import org.apache.commons.httpclient.methods.multipart.Part

class CuantoClient implements ICuantoClient {

	String cuantoUrl
	String proxyHost
	Integer proxyPort

	XStream xstream = new XStream();


	public CuantoClient() {}


	public CuantoClient(String cuantoUrl) {
		this()
		this.cuantoUrl = cuantoUrl
	}


	public Long createProject(String fullName, String projectKey, String testType) {
		return createProject(new Project(name:fullName, 'projectKey': projectKey, 'testType': testType))
	}


	public Long createProject(Project project) {
		if (!project.projectKey) {
			throw new IllegalArgumentException("Project argument must be a valid cuanto project key")
		}
		PostMethod post = getMethod("post", "${cuantoUrl}/project/create") as PostMethod
		String xmlProj = xstream.toXML(project)
		post.requestEntity = new StringRequestEntity(xmlProj, "text/xml", null)
		def projectId = null
		try {
			def responseCode = httpClient.executeMethod(post)
			def responseText = post.getResponseBodyAsStream().text.trim()
			if (responseCode == HttpStatus.SC_OK) {
				projectId = Long.valueOf(responseText)
			} else if (responseCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
				throw new CuantoClientException(responseText)
			} else {
				throw new CuantoClientException("HTTP Response code ${responseCode}: ${responseText}")
			}
		} finally {
			post.releaseConnection()
		}
		return projectId
	}


	public void deleteProject(Long projectId) {
		deleteObject "${cuantoUrl}/project/delete", projectId
	}


	public void deleteTestRun(Long testRunId) {
		deleteObject "${cuantoUrl}/testRun/delete", testRunId
	}


	public void deleteTestOutcome(Long testOutcomeId) {
		deleteObject "${cuantoUrl}/testOutcome/delete", testOutcomeId
	}


	private void deleteObject(url, objectId) {
		def post = getMethod("post", url)
		post.addParameter "id", objectId.toString()

		def responseCode
		def responseText
		try {
			responseCode = httpClient.executeMethod(post)
			responseText = post.getResponseBodyAsStream().text
		} finally {
			post.releaseConnection()
		}

		if (responseCode != HttpStatus.SC_OK) {
			throw new CuantoClientException("HTTP Response code ${responseCode}: ${responseText}")
		}
	}


	public Project getProject(Long projectId) {
		def get = getMethod("get", "${cuantoUrl}/project/get/${projectId}")
		get.addRequestHeader "Accept", "application/xml"

		def responseCode
		String responseText
		Project project
		try {
			responseCode = httpClient.executeMethod(get)
			responseText = get.getResponseBodyAsStream().text
			if (responseCode == HttpStatus.SC_OK) {
				project = xstream.fromXML(responseText) as Project
			} else if (responseCode == HttpStatus.SC_NOT_FOUND) {
				project = null
			} else {
				throw new CuantoClientException("HTTP Response code ${responseCode}: ${responseText}")
			}
		} finally {
			get.releaseConnection()
		}
		return project
	}

	
	public Map getTestRunInfo(Long testRunId) {
		return getValueMap("${cuantoUrl}/testRun/get/${testRunId.toString()}")
	}


	public TestRun getTestRun(Long testRunId) {
		def get = getMethod("get", "${cuantoUrl}/testRun/get/${testRunId.toString()}")
		get.addRequestHeader "Accept", "application/xml"

		def responseCode
		def responseText
		TestRun testRun
		try {
			responseCode = httpClient.executeMethod(get)
			responseText = get.getResponseBodyAsStream().text
			if (responseCode == HttpStatus.SC_OK) {
				testRun = (TestRun) xstream.fromXML(responseText)
			} else if (responseCode == HttpStatus.SC_NOT_FOUND) {
				testRun = null
			} else {
				throw new CuantoClientException("HTTP Response code ${responseCode}: ${responseText}")
			}
		} finally {
			get.releaseConnection()
		}
		return testRun
	}

	
	public Long createTestRun(TestRun testRun) {
		if (!testRun.projectKey) {
			throw new IllegalArgumentException("Project argument must be a valid cuanto project key")
		}
		def post = getMethod("post", "${cuantoUrl}/testRun/createXml")
		def request = new StringRequestEntity(xstream.toXML(testRun), "text/xml", null)
		post.requestEntity = request
		def testRunId = null
		try {
			def responseCode = httpClient.executeMethod(post)
			def responseText = post.getResponseBodyAsStream().text.trim()
			if (responseCode == HttpStatus.SC_OK) {
				testRunId = Long.valueOf(responseText)
			} else if (responseCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
				throw new IllegalArgumentException(responseText)
			} else {
				throw new CuantoClientException("HTTP Response code ${responseCode}: ${responseText}")
			}
		} finally {
			post.releaseConnection()
		}
		return testRunId
	}


	public TestOutcome getTestOutcome(Long testOutcomeId) {
		def url = "${cuantoUrl}/testOutcome/getXml/${testOutcomeId.toString()}"
		def get = getMethod("get", url)
		get.addRequestHeader "Accept", "text/xml"

		def responseCode
		def responseText
		try {
			responseCode = httpClient.executeMethod(get)
			responseText = get.getResponseBodyAsStream().text
			if (responseCode == HttpStatus.SC_NOT_FOUND) {
				return null
			} else {
				def outcome = (TestOutcome)xstream.fromXML(responseText)
				return outcome
			}
		} finally {
			get.releaseConnection()
		}

	}


	public void submit(File file, Long testRunId) {
		submit([file], testRunId)
	}
	

	public void submit(List<File> files, Long testRunId) {
		def fullUri = "${cuantoUrl}/testRun/submitFile"
		PostMethod post = getMethod("post", fullUri)
		post.addRequestHeader "Cuanto-TestRun-Id", testRunId.toString()

		def parts = []
		files.each { file ->
			parts += new FilePart(file.getName(), file)
		}
		post.requestEntity = new MultipartRequestEntity(parts as Part[], post.params)

		def responseCode
		def responseText
		try {
			HttpClient hclient = getHttpClient()
			responseCode = hclient.executeMethod(post)
			responseText = post.getResponseBodyAsStream().text
		} finally {
			post.releaseConnection()
		}
		if (responseCode != HttpStatus.SC_OK) {
			throw new CuantoClientException("HTTP Response code ${responseCode}: ${responseText}")
		}
	}


	public Long submit(TestOutcome testOutcome, Long testRunId, Long projectId = null) {
		def fullUri = "${cuantoUrl}/testRun/submitSingleTest"
		PostMethod post = getMethod("post", fullUri)
		if (testRunId) {
			post.addRequestHeader "Cuanto-TestRun-Id", testRunId.toString()
		}
		if (projectId) {
			post.addRequestHeader "Cuanto-Project-Id", projectId.toString()
		}

		def outcomeXml = xstream.toXML(testOutcome)
		post.requestEntity = new StringRequestEntity(outcomeXml, "text/xml", null)

		def responseCode
		def responseText
		def testOutcomeId
		try {
			HttpClient hclient = getHttpClient()
			responseCode = hclient.executeMethod(post)
			responseText = post.getResponseBodyAsStream().text
		} finally {
			post.releaseConnection()
		}

		if (responseCode == HttpStatus.SC_OK) {
			testOutcomeId = Long.valueOf(responseText)
			testOutcome.id = testOutcomeId
		} else {
			throw new CuantoClientException("HTTP Response (${responseCode}): ${responseText}")
		}
		return testOutcomeId 
	}


	// TODO: make testRunStats object in API
	Map getTestRunStats(Long testRunId){
		return getValueMap("${cuantoUrl}/testRun/statistics/${testRunId.toString()}")
	}


	private Map getValueMap(url) {
		def get = getMethod("get", url)
		get.addRequestHeader "Accept", "text/plain"

		def responseCode
		def responseText
		try {
			responseCode = httpClient.executeMethod(get)
			responseText = get.getResponseBodyAsStream().text
		} finally {
			get.releaseConnection()
		}

		def valueMap = [:]

		if (responseCode == HttpStatus.SC_OK) {
			responseText.eachLine {String line ->
				def rawValues = line.split("=")
				if (rawValues.size() > 1) {
					valueMap[rawValues[0].trim()] = rawValues[1].trim()
				}
			}
		} else {
			throw new CuantoClientException("HTTP Response code ${responseCode}: ${responseText}")
		}
		return valueMap
	}


	private HttpClient getHttpClient() {
		HttpClient httpClient = new HttpClient()
		if (proxyHost && proxyPort) {
			httpClient.hostConfiguration.setProxy proxyHost, proxyPort
		}
		return httpClient
	}


	private HttpMethod getMethod(methodType, url) {
		def method
		if (methodType.toLowerCase() == "get") {
			method = new GetMethod(url)
		} else if (methodType.toLowerCase() == "post") {
			method = new PostMethod(url)
		} else {
			throw new CuantoClientException("Unknown HTTP method: ${methodType}")
		}
		return method

	}


	public Long submitTestOutcomeForProject(TestOutcome testOutcome, Long projectId) {
		submit(testOutcome, null, projectId)
	}


	public Long submitTestOutcomeForTestRun(TestOutcome testOutcome, Long testRunId) {
		submit(testOutcome, testRunId)
	}


	public void updateTestOutcome(TestOutcome testOutcome) {
		def fullUri = "${cuantoUrl}/testOutcome/update"
		PostMethod post = getMethod("post", fullUri)
		def outcomeXml = xstream.toXML(testOutcome)
		post.requestEntity = new StringRequestEntity(outcomeXml, "text/xml", null)

		def responseCode
		def responseText
		def testOutcomeId
		try {
			HttpClient hclient = getHttpClient()
			responseCode = hclient.executeMethod(post)
			responseText = post.getResponseBodyAsStream().text
		} finally {
			post.releaseConnection()
		}

		if (responseCode != HttpStatus.SC_OK) {
			throw new CuantoClientException("HTTP Response (${responseCode}): ${responseText}")
		}
	}

}
