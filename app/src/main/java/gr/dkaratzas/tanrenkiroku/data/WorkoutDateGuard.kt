package gr.dkaratzas.tanrenkiroku.data

import java.time.LocalDate

internal class WorkoutDateGuard(initialDate: LocalDate) {
    data class Ticket(val date: LocalDate, val generation: Long)
    private var date = initialDate
    private var generation = 0L
    fun begin(date: LocalDate): Ticket { this.date = date; generation++; return Ticket(date, generation) }
    fun invalidate() { generation++ }
    fun accepts(ticket: Ticket): Boolean = ticket.date == date && ticket.generation == generation
}
