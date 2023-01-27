package com.alpdex.sered.digicon.agent;

import java.util.Calendar;
import java.util.List;

import com.alpdex.sered.allocation.vo.AllocationTimeOptionVO;
import com.alpdex.sered.digicon.agent.exception.ChecksumException;
import com.alpdex.sered.sales.parkingmeter.enumeration.CardBrandEnum;
import com.alpdex.sered.vehicle.enumeration.VehicleTypeEnum;

public interface CommandParser {

	public int getCurrentPosition();
	public void setCurrentPosition(int position);
	public int parseParkmeterCode();
	public VehicleTypeEnum parseVehicleType();
	public int parseExternalCommand();
	public int parseSequence();
	public Calendar parseEventDate();
	public String parsePrePaidCardCode();
	public String parsePrePaidCardCodeMifarePlus();
	public float parseValue();
	public String parseExternalSubCommand();
	public float parsePrePaidCardBalance();
	public String parsePlate();
	public short parseVacancyNumber();
	public short parseBatteryStatus();
	public int checksum() throws ChecksumException;
	public int parseAllocationTimeInMinutes();
	public String parseRegularizationID();
	public String parsePaymentAuthenticator();
	public CardBrandEnum parseCardBrand();
	public String parseFederalIdentification();
	public String parseUserPassword(int length);
	public String parsePrePaidCardCodeAsNotificationNumber();
	public Integer parseLoadBalanceType();
	public void clockRUF();
	public Integer parseUserId();
	public byte[] buildPriceOptions(List<AllocationTimeOptionVO> timeOptions, Calendar previousAllocationEndTime);
}