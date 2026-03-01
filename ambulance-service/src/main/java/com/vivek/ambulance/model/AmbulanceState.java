package com.vivek.ambulance.model;

public class AmbulanceState {
	private AmbulanceStatus status;
	private long version;

	public AmbulanceState(AmbulanceStatus status, long version) {
		this.status = status;
		this.version = version;
	}

	public AmbulanceStatus getStatus() {
		return status;
	}

	public void setStatus(AmbulanceStatus status) {
		this.status = status;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}
