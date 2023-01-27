package com.alpdex.sered.digicon.agent;

import com.alpdex.sered.allocation.enumeration.VacancyAreaTypeEnum;
import com.alpdex.sered.vehicle.enumeration.VehicleTypeEnum;

public class DigiconAllocationTypeVO {
	private VehicleTypeEnum vehicleType;
	private Double value;
	private Integer timeType;
	private Integer amount;
	private VacancyAreaTypeEnum vacancyAreaTypeEnum;

	public VehicleTypeEnum getVehicleType() {
		return vehicleType;
	}

	public void setVehicleType(VehicleTypeEnum vehicleType) {
		this.vehicleType = vehicleType;
	}

	public Double getValue() {
		return value;
	}

	public void setValue(Double value) {
		this.value = value;
	}

	public Integer getTimeType() {
		return timeType;
	}

	public void setTimeType(Integer timeType) {
		this.timeType = timeType;
	}

	public Integer getAmount() {
		return amount;
	}

	public void setAmount(Integer amount) {
		this.amount = amount;
	}

	public VacancyAreaTypeEnum getVacancyAreaTypeEnum() {
		return vacancyAreaTypeEnum;
	}

	public void setVacancyAreaTypeEnum(VacancyAreaTypeEnum vacancyAreaTypeEnum) {
		this.vacancyAreaTypeEnum = vacancyAreaTypeEnum;
	}

}
