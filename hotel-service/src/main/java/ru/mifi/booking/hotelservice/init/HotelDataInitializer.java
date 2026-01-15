package ru.mifi.booking.hotelservice.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mifi.booking.hotelservice.entity.Hotel;
import ru.mifi.booking.hotelservice.entity.Room;
import ru.mifi.booking.hotelservice.repository.HotelRepository;
import ru.mifi.booking.hotelservice.repository.RoomRepository;

import java.util.List;

/**
 * Инициализатор данных hotel-service.
 *
 * <p>
 * Цель — после старта иметь 3 отеля и по 2–4 комнаты,
 * чтобы проверяющий мог сразу сделать ручной прогон (GET hotels/rooms + booking).
 * </p>
 *
 * <p>
 * Данные добавляю только если база пустая.
 * Для H2 in-memory это означает — "каждый запуск будет с демо-данными".
 * </p>
 */
@Component
public class HotelDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HotelDataInitializer.class);

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    public HotelDataInitializer(HotelRepository hotelRepository, RoomRepository roomRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hotelRepository.count() > 0 || roomRepository.count() > 0) {
            log.info("Hotels/rooms already exist. Skipping seeding.");
            return;
        }

        Hotel h1 = hotelRepository.save(new Hotel(null, "Hotel One", "Moscow"));
        Hotel h2 = hotelRepository.save(new Hotel(null, "Hotel Two", "Saint Petersburg"));
        Hotel h3 = hotelRepository.save(new Hotel(null, "Hotel Three", "Kazan"));

        seedRooms(h1, List.of("101", "102", "201"));
        seedRooms(h2, List.of("10", "11"));
        seedRooms(h3, List.of("1A", "1B", "2A", "2B"));

        log.info("Seeded initial hotels and rooms (3 hotels, 9 rooms)");
    }

    private void seedRooms(Hotel hotel, List<String> numbers) {
        for (String number : numbers) {
            roomRepository.save(new Room(null, hotel, number, true, 0));
        }
    }
}
