package zerobase.weather.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zerobase.weather.domain.DateWeather;

@Repository
public interface DataWeatherRepository extends JpaRepository<DateWeather, LocalDate> {
	List<DateWeather> findAllByDate(LocalDate localDate);
}
