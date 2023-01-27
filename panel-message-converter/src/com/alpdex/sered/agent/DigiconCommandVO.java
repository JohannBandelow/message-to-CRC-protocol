package com.alpdex.sered.digicon.agent;

import java.util.Map;

import com.alpdex.sered.sales.enumeration.ParkingMeterStatusEnum;
import com.alpdex.sered.sales.enumeration.SalePointStatusEnum;
import com.alpdex.sered.sales.parkingmeter.enumeration.CommandTypeEnum;
import com.alpdex.sered.vehicle.enumeration.VehicleTypeEnum;

public class DigiconCommandVO {
	private Integer code;
	private String description;
	private CommandTypeEnum commandType;
	private Map<String, DigiconCommandVO> commandTypeVariationMap = null;
	private VehicleTypeEnum vehicleType;
	private SalePointStatusEnum status;
	private Map<String, DigiconCommandVO> statusVariationMap = null;
	private ParkingMeterStatusEnum statusType;
	private Map<String, DigiconCommandVO> statusTypeVariationMap = null;
	
	
	public Integer getCode() {
		return code;
	}

	public void setCode(Integer code) {
		this.code = code;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public CommandTypeEnum getCommandType() {
		return commandType;
	}

	public void setCommandType(CommandTypeEnum commandType) {
		this.commandType = commandType;
	}

	public VehicleTypeEnum getVehicleType() {
		return vehicleType;
	}

	public void setVehicleType(VehicleTypeEnum vehicleType) {
		this.vehicleType = vehicleType;
	}

	public Map<String, DigiconCommandVO> getCommandTypeVariationMap() {
		return commandTypeVariationMap;
	}

	public void setCommandTypeVariationMap(Map<String, DigiconCommandVO> commandTypeVariationMap) {
		this.commandTypeVariationMap = commandTypeVariationMap;
	}

	public SalePointStatusEnum getStatus() {
		return status;
	}

	public void setStatus(SalePointStatusEnum status) {
		this.status = status;
	}

	public Map<String, DigiconCommandVO> getStatusVariationMap() {
		return statusVariationMap;
	}

	public void setStatusVariationMap(Map<String, DigiconCommandVO> statusVariationMap) {
		this.statusVariationMap = statusVariationMap;
	}

	public ParkingMeterStatusEnum getStatusType() {
		return statusType;
	}

	public void setStatusType(ParkingMeterStatusEnum statusType) {
		this.statusType = statusType;
	}

	public Map<String, DigiconCommandVO> getStatusTypeVariationMap() {
		return statusTypeVariationMap;
	}

	public void setStatusTypeVariationMap(Map<String, DigiconCommandVO> statusTypeVariationMap) {
		this.statusTypeVariationMap = statusTypeVariationMap;
	}

	
}
