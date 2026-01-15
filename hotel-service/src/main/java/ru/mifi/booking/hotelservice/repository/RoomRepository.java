package ru.mifi.booking.hotelservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mifi.booking.hotelservice.entity.Room;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * Пессимистическая блокировка строки room при подтверждении доступности.
     *
     * <p>
     * Нужна, чтобы конкурентные confirm-availability на один и тот же roomId
     * сериализовались на уровне БД и не создавали пересекающиеся блокировки.
     * </p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    /**
     * Все номера, которые "в принципе доступны" (не выведены из эксплуатации).
     */
    @Query("select r from Room r where r.available = true")
    List<Room> findAllAvailable();

    /**
     * Все номера конкретного отеля.
     *
     * @param hotelId идентификатор отеля
     * @return список номеров
     */
    @Query("select r from Room r where r.hotel.id = :hotelId")
    List<Room> findAllByHotelId(@Param("hotelId") Long hotelId);
}

