package se.repos.indexing.standalone;

public class IndexingLambdaOutputObject {

    private String result;

    private String requestId;

    public String getResult() {
        return result;
    }

    public String getRequestId() {
        return requestId;
    }

    public IndexingLambdaOutputObject setResult(String result) {
        this.result = result;
        return this;
    }

    public IndexingLambdaOutputObject setRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
}
