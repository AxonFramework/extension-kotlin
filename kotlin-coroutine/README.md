# Axon Framework - Kotlin Coroutine Extension

> :warning: **Work in progress**: For now this is an experimental branch!

## Usage

For now, it provides a `CoroutineEventProcessor` which can be used. Fist you have to build this branch and install it.

Then you need to add:
```xml
<dependency>
    <groupId>org.axonframework.extensions.kotlin</groupId>
    <artifactId>kotlin-coroutine</artifactId>
    <version>4.7.0-SNAPSHOT</version>
</dependency>
```

Or similar to your dependencies. Then you can use it, for example with Spring Boot:

```kotlin
@Component
class Processor(
    eventStore: EventStore,
    tokenStore: TokenStore
) {
    companion object {
        val am = AtomicInteger(0)
    }
    var cep = CoroutineEventProcessor(
        processorName = "test",
        messageSource = eventStore as StreamableMessageSource<TrackedEventMessage<Any>>,
        tokenStore = tokenStore,
    )

    @PostConstruct
    fun start() {
        logger.info("started some kotlin")
        val task = ProcessorTask(
            { true },
            { n -> something(n) }
        )
        cep.addTask(task)
        runBlocking { cep.start() }
    }

    suspend fun something(event: TrackedEventMessage<Any>) {
        val newValue = am.getAndIncrement()
        if(newValue % 5 == 0){
            throw java.lang.RuntimeException("Counter is dividable by 5: [${newValue}], event: ${event.payload}")
        }else{
            logger.info("processed: {}", event.payload)
        }
    }
}
```