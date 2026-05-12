// 강의(Class) 생성·상태 전이·조회를 담당하는 application service.
package com.example.liveclass.application.clazz;

import com.example.liveclass.application.user.UserApplicationService;
import com.example.liveclass.domain.clazz.AccessDeniedDomainException;
import com.example.liveclass.domain.clazz.Capacity;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.clazz.ClassNotFoundException;
import com.example.liveclass.domain.clazz.ClassPeriod;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.ClassStatus;
import com.example.liveclass.domain.clazz.ConcurrentClassUpdateException;
import com.example.liveclass.domain.clazz.Money;
import com.example.liveclass.domain.clazz.event.ClassClosedEvent;
import com.example.liveclass.domain.clazz.event.ClassOpenedEvent;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.web.clazz.dto.CreateClassRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Service
public class ClassApplicationService {

    private final ClassRepository classRepository;
    private final UserApplicationService userApplicationService;
    private final ApplicationEventPublisher eventPublisher;

    public ClassApplicationService(ClassRepository classRepository,
                                   UserApplicationService userApplicationService,
                                   ApplicationEventPublisher eventPublisher) {
        this.classRepository = classRepository;
        this.userApplicationService = userApplicationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Class createDraft(UUID creatorId, CreateClassRequest req) {
        requireCreator(creatorId);

        Instant now = Instant.now();
        Money price = Money.of(req.priceAmount(), Currency.getInstance(req.priceCurrency()));
        Capacity capacity = Capacity.of(req.capacity());
        ClassPeriod period = ClassPeriod.of(req.startDate(), req.endDate());

        Class clazz = Class.draft(
                UserId.of(creatorId),
                req.title(),
                req.description(),
                price,
                capacity,
                period,
                now
        );
        return classRepository.save(clazz);
    }

    @Transactional
    public Class transitionStatus(UUID classId, UUID requesterId, ClassStatus target) {
        requireCreator(requesterId);

        if (target != ClassStatus.OPEN && target != ClassStatus.CLOSED) {
            throw new IllegalArgumentException("Unsupported transition target: " + target);
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Class clazz = classRepository.findByIdForUpdate(classId)
                        .orElseThrow(ClassNotFoundException::new);
                UserId requester = UserId.of(requesterId);
                Instant now = Instant.now();

                if (target == ClassStatus.OPEN) {
                    clazz.open(requester, now);
                } else {
                    clazz.close(requester, now);
                }

                Class saved = classRepository.save(clazz);

                ClassId savedClassId = ClassId.of(saved.getId());
                if (target == ClassStatus.OPEN) {
                    eventPublisher.publishEvent(new ClassOpenedEvent(savedClassId, requester, now));
                } else {
                    eventPublisher.publishEvent(new ClassClosedEvent(savedClassId, requester, now));
                }
                return saved;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == 1) {
                    throw new ConcurrentClassUpdateException();
                }
            }
        }
        // Unreachable — loop either returns or throws.
        throw new ConcurrentClassUpdateException();
    }

    @Transactional
    public void autoClose(UUID classId, Instant now) {
        Class c = classRepository.findById(classId)
                .orElseThrow(ClassNotFoundException::new);
        c.autoClose(now);
        classRepository.save(c);
        eventPublisher.publishEvent(
                new ClassClosedEvent(ClassId.of(classId), null, now));
    }

    @Transactional(readOnly = true)
    public Class getById(UUID classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> new ClassNotFoundException(classId));
    }

    @Transactional(readOnly = true)
    public Page<Class> listOpenClasses(Pageable pageable) {
        return classRepository.findByStatusOrderByCreatedAtDesc(ClassStatus.OPEN, pageable);
    }

    private void requireCreator(UUID userId) {
        User user = userApplicationService.getById(userId);
        if (!user.isCreator()) {
            throw new AccessDeniedDomainException(
                    "Only users with CREATOR role can perform this operation");
        }
    }
}
