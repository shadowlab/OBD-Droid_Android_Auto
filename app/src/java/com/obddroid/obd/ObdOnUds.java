package com.obddroid.obd;

import java.util.Locale;

/**
 * Translation between classic OBD-II services (SAE J1979) and OBD on UDS
 * (SAE J1979-2):
 *
 * PIDs (service 01), translated line by line (see fromUdsResponse):
 *   01 PP          ->  22 F4 PP        62 F4 PP ..  ->  41 PP ..
 *
 * Whole messages (see fromUdsMessage), after multi-frame assembly:
 *   03 (stored)    ->  19 42 33 08 FF  59 42 33 .. records  ->  43 NN DTC..
 *   07 (pending)   ->  19 42 33 04 FF  59 42 33 .. records  ->  47 NN DTC..
 *   0A (permanent) ->  19 55 33        59 55 33 .. records  ->  4A NN DTC..
 *   04 (clear)     ->  14 FF FF 33     54                   ->  44
 *   09 PP          ->  22 F8 PP        62 F8 PP ..          ->  49 PP ..
 *   negative responses: 7F SS NRC -> 7F <classic service> NRC
 *
 * UDS DTCs are 3 bytes (2 byte DTC + failure type byte); the classic
 * 2 byte DTC is kept.
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

	/** ReadDTCInformation, emissions-related group, severity mask: any */
	private static final String UDS_READ_DTC_CONFIRMED = "19423308FF";
	private static final String UDS_READ_DTC_PENDING = "19423304FF";
	private static final String UDS_READ_DTC_PERMANENT = "195533";
	/** ClearDiagnosticInformation, emissions-related group */
	private static final String UDS_CLEAR_DTC = "14FFFF33";

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
	 * Translate a fault code, clear code or vehicle information request
	 * to its OBD on UDS equivalent. The response is translated as a whole
	 * message with fromUdsMessage().
	 *
	 * @param request request as sent to the adapter, e.g. "03" or "0902"
	 * @return UDS request, or null if not a translated request
	 */
	public static String toUdsMessageRequest(String request)
	{
		if (request == null)
		{
			return null;
		}
		String req = request.trim().toUpperCase(Locale.ROOT);
		switch (req)
		{
			case "03":
				return UDS_READ_DTC_CONFIRMED;
			case "07":
				return UDS_READ_DTC_PENDING;
			case "0A":
				return UDS_READ_DTC_PERMANENT;
			case "04":
				return UDS_CLEAR_DTC;
			default:
				if (req.length() == 4 && req.startsWith("09") && isHex(req.substring(2)))
				{
					return "22F8" + req.substring(2);
				}
				return null;
		}
	}

	/**
	 * Translate a complete (multi-frame assembled) response message to a request
	 * made with toUdsMessageRequest() back to the classic service format.
	 *
	 * @param message        response payload without CAN header, e.g. "5942330800010301001008"
	 * @param classicRequest the original classic request, e.g. "03"
	 * @return translated message, or null if the message needs no translation
	 */
	public static String fromUdsMessage(String message, String classicRequest)
	{
		if (message == null || classicRequest == null || !isHex(message) || message.length() < 2)
		{
			return null;
		}
		String req = classicRequest.trim().toUpperCase(Locale.ROOT);
		String classicService = req.substring(0, 2);
		String msg = message.toUpperCase(Locale.ROOT);

		// negative response: 7F <UDS service> NRC -> 7F <classic service> NRC
		if (msg.startsWith("7F") && msg.length() >= 6)
		{
			return "7F" + classicService + msg.substring(4);
		}

		try
		{
			switch (req)
			{
				case "03":
				case "07":
					// 59 42 FG DSAM DSevAM DFI {Sev DTC(3) Status}
					if (msg.startsWith("5942") && msg.length() >= 12)
					{
						return dtcList(classicService, msg.substring(12), 10, 2);
					}
					return null;

				case "0A":
					// 59 55 FG DSAM DFI {DTC(3) Status}
					if (msg.startsWith("5955") && msg.length() >= 10)
					{
						return dtcList(classicService, msg.substring(10), 8, 0);
					}
					return null;

				case "04":
					return msg.startsWith("54") ? "44" : null;

				default:
					// 62 F8 PP data -> 49 PP data
					if (req.startsWith("09") && msg.startsWith("62F8" + req.substring(2)))
					{
						String infoType = req.substring(2);
						String data = msg.substring(6);
						// classic VIN response carries a message count (01) before the 17 characters
						if (infoType.equals("02") && data.length() == 34)
						{
							data = "01" + data;
						}
						return "49" + infoType + data;
					}
					return null;
			}
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * Build a classic DTC response (service byte + number of DTCs + 2 byte DTCs)
	 * from UDS DTC records.
	 *
	 * @param classicService classic request service, e.g. "03"
	 * @param records        hex string of DTC records
	 * @param recordLen      length of one record in hex digits
	 * @param dtcOffset      offset of the 3 byte DTC within a record in hex digits
	 */
	private static String dtcList(String classicService, String records, int recordLen, int dtcOffset)
	{
		StringBuilder dtcs = new StringBuilder();
		int count = 0;
		for (int i = 0; i + recordLen <= records.length(); i += recordLen)
		{
			// high and middle byte of the 3 byte DTC form the classic DTC
			String dtc = records.substring(i + dtcOffset, i + dtcOffset + 4);
			if (!dtc.equals("0000"))
			{
				dtcs.append(dtc);
				count++;
			}
		}
		int responseService = Integer.parseInt(classicService, 16) | 0x40;
		return String.format("%02X%02X", responseService, Math.min(count, 0x7F)) + dtcs;
	}

	/**
	 * Translate a raw multi-line adapter response (space separated lines,
	 * headers off) to a request made with toUdsMessageRequest() to classic format.
	 * Multi-frame responses ("00F 0:... 1:...") are assembled first.
	 *
	 * @param rawResponse    raw response lines separated by whitespace
	 * @param classicRequest the original classic request, e.g. "03"
	 * @return translated response lines separated by spaces (untranslatable lines kept)
	 */
	public static String rawResponseToClassic(String rawResponse, String classicRequest)
	{
		if (rawResponse == null)
		{
			return null;
		}
		StringBuilder result = new StringBuilder();
		StringBuilder frame = null;
		int frameLen = 0;
		for (String line : rawResponse.trim().split("\\s+"))
		{
			if (line.isEmpty())
			{
				continue;
			}
			if (line.length() == 3 && isHex(line))
			{
				// ISO-TP length line starts a multi-frame message
				flushFrame(result, frame, frameLen, classicRequest);
				frame = new StringBuilder();
				frameLen = Integer.parseInt(line, 16) * 2;
			}
			else if (frame != null && line.length() > 2 && line.charAt(1) == ':')
			{
				frame.append(line.substring(2));
			}
			else
			{
				flushFrame(result, frame, frameLen, classicRequest);
				frame = null;
				appendTranslated(result, line, classicRequest);
			}
		}
		flushFrame(result, frame, frameLen, classicRequest);
		return result.toString().trim();
	}

	private static void flushFrame(StringBuilder result, StringBuilder frame, int frameLen, String classicRequest)
	{
		if (frame != null && frame.length() > 0)
		{
			String msg = frame.length() > frameLen ? frame.substring(0, frameLen) : frame.toString();
			appendTranslated(result, msg, classicRequest);
		}
	}

	private static void appendTranslated(StringBuilder result, String message, String classicRequest)
	{
		String translated = fromUdsMessage(message, classicRequest);
		result.append(translated != null ? translated : message).append(' ');
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
