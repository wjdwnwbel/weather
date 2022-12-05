package zerobase.weather.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import zerobase.weather.WeatherApplication;
import zerobase.weather.domain.DateWeather;
import zerobase.weather.domain.Diary;
import zerobase.weather.error.InvalidDate;
import zerobase.weather.repository.DataWeatherRepository;
import zerobase.weather.repository.DiaryRepository;

@Service
@Transactional(readOnly = true)
public class DiaryService {
	private final DiaryRepository diaryRepository;
	private final DataWeatherRepository dataWeatherRepository;
	private static final Logger logger = LoggerFactory.getLogger(WeatherApplication.class);

	// 환경과 무관하게 가져올 수 있음
	@Value("${openweathermap.key}")
	private String apiKey;

	public DiaryService(DiaryRepository diaryRepository,
		DataWeatherRepository dataWeatherRepository) {
		this.diaryRepository = diaryRepository;
		this.dataWeatherRepository = dataWeatherRepository;
	}

	// 매 시간마다 날씨저장
	@Transactional
	@Scheduled(cron = "0 0 1 * * *")
	public void saveWeatherDate() {
		logger.info("오늘도 날씨 데이터 잘 가져옴");
		dataWeatherRepository.save(getWeatherFromApi());
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public void createDiary(LocalDate date, String text) {
		logger.info("started to create diary");
		// API 혹은 DB에서 데이터 가져오기
		DateWeather dateWeather = getDateWeather(date);

		Diary nowDiary = new Diary();
		nowDiary.setDateWeather(dateWeather);
		nowDiary.setText(text);

		diaryRepository.save(nowDiary);

		logger.info("end to create diary");
	}

	// DB에서 weather 데이터 가져오기
	private DateWeather getDateWeather(LocalDate date) {
		List<DateWeather> dateWeatherListFromDB = dataWeatherRepository.findAllByDate(date);

		if(dateWeatherListFromDB.size() == 0) {
			// 없으니까 api에서 날씨는 가져와야하는데 정책상 현재 날씨를 가져오도록하거나 없이 쓰도록
			return getWeatherFromApi();
		} else {
			return dateWeatherListFromDB.get(0);
		}
	}


	// open weather map 데이터 받아오기 - API
	private String getWeatherString() {
		String apiUrl = "https://api.openweathermap.org/data/2.5/weather?q=seoul&appid=" + apiKey;

		try {
			URL url = new URL(apiUrl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			int responseCode = connection.getResponseCode();

			BufferedReader br;
			if(responseCode == 200) {	// 응답 객체 혹은 에러
				br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			} else {
				br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
			}

			String inputLine;
			StringBuilder response = new StringBuilder();
			while ((inputLine = br.readLine()) != null) {
				response.append(inputLine);
			}
			br.close();

			return response.toString();
		} catch (Exception e) {
			return "failed to get response";
		}
	}

	// 데이터 파싱
	private Map<String, Object> parseWeather(String jsonString) {
		JSONParser jsonParser = new JSONParser();
		JSONObject jsonObject;

		try {
			jsonObject = (JSONObject) jsonParser.parse(jsonString);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}

		Map<String, Object> resultMap = new HashMap<>();
		JSONObject mainData = (JSONObject) jsonObject.get("main");
		resultMap.put("temp", mainData.get("temp"));

		JSONArray weatherArray = (JSONArray) jsonObject.get("weather");
		JSONObject weatherData = (JSONObject) weatherArray.get(0);
		resultMap.put("main", weatherData.get("main"));
		resultMap.put("icon", weatherData.get("icon"));

		return resultMap;
	}

	@Transactional(readOnly = true)
	public List<Diary> readDiary(LocalDate date) {
		/*
		if(date.isAfter(LocalDate.ofYearDay(3050, 1))) {
			throw new InvalidDate();
		}
		*/
		return diaryRepository.findAllByDate(date);
	}


	public List<Diary> readDiaries(LocalDate startDate, LocalDate endDate) {
		return diaryRepository.findAllByDateBetween(startDate, endDate);
	}

	// 날짜중에 첫번째것을 수정한다고 가정
	public void updateDiary(LocalDate date, String text) {
		Diary nowDiary = diaryRepository.getFirstByDate(date);
		nowDiary.setText(text);

		diaryRepository.save(nowDiary);
	}

	public void deleteDiary(LocalDate date) {
		diaryRepository.deleteAllByDate(date);
	}

	// 미리 날씨를 가져오는 함수
	private DateWeather getWeatherFromApi() {
		String weatherData = getWeatherString();
		Map<String, Object> parsedWeather = parseWeather(weatherData);

		DateWeather dateWeather = new DateWeather();
		dateWeather.setDate(LocalDate.now());
		dateWeather.setWeather(parsedWeather.get("main").toString());
		dateWeather.setIcon(parsedWeather.get("icon").toString());
		dateWeather.setTemperature((Double) parsedWeather.get("temp"));

		return dateWeather;
	}
}
