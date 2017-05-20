package com.semantria.interfaces;

import com.semantria.mapping.output.CollAnalyticData;
import com.semantria.mapping.output.DocAnalyticData;
import com.semantria.utils.RequestArgs;
import com.semantria.utils.ResponseArgs;

import java.util.List;

public interface ICallbackHandler
{
	void onResponse(Object sender, ResponseArgs responseArgs);
	void onRequest(Object sender, RequestArgs requestArgs);
	void onError(Object sender, ResponseArgs errorArgs);
	void onDocsAutoResponse(Object sender, List<DocAnalyticData> processedData);
	void onCollsAutoResponse(Object sender, List<CollAnalyticData> processedData);

}
