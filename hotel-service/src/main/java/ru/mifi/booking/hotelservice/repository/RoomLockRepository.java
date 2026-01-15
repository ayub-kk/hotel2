package ru.mifi.booking.hotelservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mifi.booking.hotelservice.entity.Room;
import ru.mifi.booking.hotelservice.entity.RoomLock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomLockRepository extends JpaRepository<RoomLock, Long> {

    /**
     * Проверка пересечения периодов.
     * Пересечение есть, если НЕ выполняется условие "lock полностью до start" И "lock полностью после end".
     *
     * @param room  номер, для которого ищем пересечения
     * @param start начало периода
     * @param end   конец периода
     * @return список блокировок, пересекающихся с заданным периодом
     */
    @Query("select rl from RoomLock rl where rl.room = :room and not (rl.endDate <= :start or rl.startDate >= :end)")
    List<RoomLock> findOverlaps(
            @Param("room") Room room,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /**
     * Все блокировки по отелю в заданном периоде (для статистики).
     *
     * @param hotelId идентификатор отеля
     * @param start   начало периода
     * @param end     конец периода
     * @return список блокировок, которые пересекаются с периодом
     */
    @Query("select rl from RoomLock rl where rl.room.hotel.id = :hotelId and not (rl.endDate <= :start or rl.startDate >= :end)")
    List<RoomLock> findOverlapsInHotel(
            @Param("hotelId") Long hotelId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /**
     * Поиск блокировки по bookingId.
     *
     * @param bookingId идентификатор бронирования
     * @return блокировка, если найдена
     */
    Optional<RoomLock> findByBookingId(String bookingId);

    /**
     * Поиск блокировки по requestId (для идемпотентности).
     *
     * @param requestId идентификатор запроса
     * @return блокировка, если найдена
     */
    Optional<RoomLock> findByRequestId(String requestId);

    /**
     * Удалить все блокировки конкретного номера.
     *
     * @param roomId id номера
     */
    void deleteAllByRoom_Id(Long roomId);
}
