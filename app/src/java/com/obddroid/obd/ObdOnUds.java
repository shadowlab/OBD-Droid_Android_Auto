package com.obddroid.obd;

import java.util.Locale;

/**
 * Translation between classic OBD-II service 01 (SAE J1979) and OBD on UDS
 * (SAE J1979-2), where the same PIDs are read with UDS ReadDataByIdentifier
 * (service 0x22) using DIDs 0xF400-0xF4FF.
 *
 * Requests:  01 PP        ->  22 F4 PP
 * Responses: 62 F4 PP ..  ->  41 PP ..      (negative: 7F 22 NRC -> 7F 01 NRC)
 *
 * Responses are translated in the ELM327 text formats this app uses (ATS0):
 * without headers ("62F40D00"), with 11 bit or 29 bit CAN headers followed
 * by the ISO-TP PCI ("7E80462F40D00", "18DAF1620462F40D00"), and the ISO-TP
 * length line of multi-frame responses without headers ("00B"). The PCI and
 * length are reduced by one byte, since the response loses the DID high byte.
 */
public final class ObdOnUds
{
	private static final String UDS_POS_RSP = "62F4";
	private static final String UDS_NEG_RSP = "7F22";
	private static final String OBD_POS_RSP = "41";
	private static final String OBD_NEG_RSP = "7F01";

	/** header length in hex digits: none, 11 bit CAN, 29 bit CAN */
	private static final int[] HEADER_LENGTHS = {0, 3, 8};

	private ObdOnUds()
	{
	}

	/**
	 * Translate a service 01 request to its OBD on UDS equivalent.
	 *
	 * @param request request as sent to the adapter, e.g. "010D"
	 * @return UDS request, e.g. "22F40D", or null if not a service 01 PID request
	 */
	public static String toUdsRequest(String request)
	{
		if (request != null
			&& request.length() == 4
			&& request.startsWith("01")
			&& isHex(request.substring(2)))
		{
			return "22F4" + request.substring(2).toUpperCase(Locale.ROOT);
		}
		return null;
	}

	/**
	 * Translate a response line to an OBD on UDS request back to service 01 format.
	 *
	 * @param line response line from the adapter (spaces removed)
	 * @return translated line, or null if the line needs no translation
	 */
	public static String fromUdsResponse(String line)
	{
		if (line == null || line.isEmpty())
		{
			return null;
		}

		// ISO-TP length line of a multi-frame response without headers ("00B")
		if (line.length() == 3 && isHex(line))
		{
			int len = Integer.parseInt(line, 16);
			return len > 0 ? String.format("%03X", len - 1) : null;
		}

		// multi-frame first line without headers ("0:62F49A...")
		if (line.startsWith("0:"))
		{
			String payload = translatePayload(line.substring(2));
			return payload != null ? "0:" + payload : null;
		}

		// single frame, with or without CAN header
		for (int hdrLen : HEADER_LENGTHS)
		{
			if (hdrLen == 0)
			{
				String payload = translatePayload(line);
				if (payload != null)
				{
					return payload;
				}
				continue;
			}

			// header + PCI "0L" (single frame)
			int payloadStart = hdrLen + 2;
			if (line.length() > payloadStart
				&& isHex(line.substring(0, payloadStart))
				&& line.charAt(hdrLen) == '0')
			{
				String payload = translatePayload(line.substring(payloadStart));
				if (payload != null)
				{
					int len = Integer.parseInt(line.substring(hdrLen, payloadStart), 16);
					return line.substring(0, hdrLen) + String.format("%02X", len - 1) + payload;
				}
			}

			// header + PCI "1LLL" (first frame of a multi-frame response)
			payloadStart = hdrLen + 4;
			if (line.length() > payloadStart
				&& isHex(line.substring(0, payloadStart))
				&& line.charAt(hdrLen) == '1')
			{
				String payload = translatePayload(line.substring(payloadStart));
				if (payload != null)
				{
					int len = Integer.parseInt(line.substring(hdrLen + 1, payloadStart), 16);
					return line.substring(0, hdrLen) + String.format("1%03X", len - 1) + payload;
				}
			}
		}
		return null;
	}

	/**
	 * Translate a UDS payload ("62F4PP..." or "7F22NN") to service 01 format.
	 *
	 * @return translated payload, or null if not an OBD on UDS response
	 */
	private static String translatePayload(String payload)
	{
		if (payload.startsWith(UDS_POS_RSP) && payload.length() >= 6)
		{
			return OBD_POS_RSP + payload.substring(UDS_POS_RSP.length());
		}
		if (payload.startsWith(UDS_NEG_RSP) && payload.length() >= 6)
		{
			return OBD_NEG_RSP + payload.substring(UDS_NEG_RSP.length());
		}
		return null;
	}

	private static boolean isHex(String s)
	{
		if (s.isEmpty())
		{
			return false;
		}
		for (int i = 0; i < s.length(); i++)
		{
			if (Character.digit(s.charAt(i), 16) < 0)
			{
				return false;
			}
		}
		return true;
	}
}
