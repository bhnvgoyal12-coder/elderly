package com.example.elderlylauncher.util

import java.util.Calendar

/**
 * Provides daily quotes curated for an elderly audience.
 * Quotes rotate twice daily (morning and evening).
 */
object QuoteProvider {

    data class Quote(
        val text: String,
        val author: String
    )

    private val quotes = listOf(
        // Wisdom and Life Lessons
        Quote("The best time to plant a tree was 20 years ago. The second best time is now.", "Chinese Proverb"),
        Quote("It is not how old you are, but how you are old.", "Jules Renard"),
        Quote("Age is an issue of mind over matter. If you don't mind, it doesn't matter.", "Mark Twain"),
        Quote("The longer I live, the more beautiful life becomes.", "Frank Lloyd Wright"),
        Quote("With age comes wisdom, but sometimes age comes alone.", "Oscar Wilde"),
        Quote("Life is what happens when you're busy making other plans.", "John Lennon"),
        Quote("In the end, it's not the years in your life that count. It's the life in your years.", "Abraham Lincoln"),
        Quote("The secret of staying young is to live honestly, eat slowly, and lie about your age.", "Lucille Ball"),

        // Gratitude and Happiness
        Quote("Happiness is not something ready-made. It comes from your own actions.", "Dalai Lama"),
        Quote("The greatest glory in living lies not in never falling, but in rising every time we fall.", "Nelson Mandela"),
        Quote("Count your age by friends, not years. Count your life by smiles, not tears.", "John Lennon"),
        Quote("Gratitude turns what we have into enough.", "Anonymous"),
        Quote("The most important thing is to enjoy your life — to be happy — it's all that matters.", "Audrey Hepburn"),
        Quote("Happiness is a warm puppy.", "Charles M. Schulz"),
        Quote("The purpose of our lives is to be happy.", "Dalai Lama"),

        // Family and Love
        Quote("Family is not an important thing. It's everything.", "Michael J. Fox"),
        Quote("The love of family is life's greatest blessing.", "Anonymous"),
        Quote("A happy family is but an earlier heaven.", "George Bernard Shaw"),
        Quote("Where there is love there is life.", "Mahatma Gandhi"),
        Quote("The best thing to hold onto in life is each other.", "Audrey Hepburn"),
        Quote("Love is composed of a single soul inhabiting two bodies.", "Aristotle"),

        // Inspiration and Courage
        Quote("You are never too old to set another goal or to dream a new dream.", "C.S. Lewis"),
        Quote("It does not matter how slowly you go as long as you do not stop.", "Confucius"),
        Quote("The only way to do great work is to love what you do.", "Steve Jobs"),
        Quote("Believe you can and you're halfway there.", "Theodore Roosevelt"),
        Quote("Keep your face always toward the sunshine, and shadows will fall behind you.", "Walt Whitman"),
        Quote("Every day may not be good, but there is something good in every day.", "Anonymous"),
        Quote("Do what you can, with what you have, where you are.", "Theodore Roosevelt"),

        // Peace and Contentment
        Quote("Peace begins with a smile.", "Mother Teresa"),
        Quote("Simplicity is the ultimate sophistication.", "Leonardo da Vinci"),
        Quote("The greatest wealth is to live content with little.", "Plato"),
        Quote("Nature does not hurry, yet everything is accomplished.", "Lao Tzu"),
        Quote("Be yourself; everyone else is already taken.", "Oscar Wilde"),
        Quote("The quieter you become, the more you can hear.", "Ram Dass"),

        // Humor and Joy
        Quote("A day without laughter is a day wasted.", "Charlie Chaplin"),
        Quote("Wrinkles should merely indicate where the smiles have been.", "Mark Twain"),
        Quote("You're only as old as you feel.", "Anonymous"),
        Quote("I'm not 80, I'm 18 with 62 years of experience.", "Anonymous"),
        Quote("Growing old is mandatory, but growing up is optional.", "Walt Disney"),
        Quote("Age is strictly a case of mind over matter. If you don't mind, it doesn't matter.", "Jack Benny"),

        // Kindness and Giving
        Quote("No act of kindness, no matter how small, is ever wasted.", "Aesop"),
        Quote("The best way to find yourself is to lose yourself in the service of others.", "Mahatma Gandhi"),
        Quote("We make a living by what we get, but we make a life by what we give.", "Winston Churchill"),
        Quote("Kindness is a language which the deaf can hear and the blind can see.", "Mark Twain"),
        Quote("Be kind whenever possible. It is always possible.", "Dalai Lama"),

        // Health and Wellness
        Quote("Take care of your body. It's the only place you have to live.", "Jim Rohn"),
        Quote("A good laugh and a long sleep are the best cures in the doctor's book.", "Irish Proverb"),
        Quote("To keep the body in good health is a duty, otherwise we shall not be able to keep our mind strong and clear.", "Buddha"),
        Quote("Walking is the best possible exercise.", "Thomas Jefferson"),

        // Morning Quotes
        Quote("Every morning brings new potential, but if you dwell on the misfortunes of the day before, you tend to overlook tremendous opportunities.", "Harvey Mackay"),
        Quote("Rise up, start fresh, see the bright opportunity in each new day.", "Anonymous"),
        Quote("Today is a new day. Don't let your history interfere with your destiny.", "Steve Maraboli"),

        // Evening Quotes
        Quote("Finish each day and be done with it. Tomorrow is a new day.", "Ralph Waldo Emerson"),
        Quote("Rest when you're weary. Refresh and renew yourself.", "Ralph Marston"),
        Quote("Each night, when I go to sleep, I die. And the next morning, when I wake up, I am reborn.", "Mahatma Gandhi")
    )

    /**
     * Gets the quote for the current time.
     * Changes twice daily - morning (before 2pm) and evening (after 2pm).
     */
    fun getQuoteOfTheDay(): Quote {
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        // Create a seed based on date and time of day
        val timeSlot = if (hour < 14) 0 else 1 // Morning or evening
        val seed = (year * 1000 + dayOfYear) * 2 + timeSlot

        // Use the seed to pick a quote deterministically
        val index = (seed % quotes.size).let { if (it < 0) it + quotes.size else it }
        return quotes[index]
    }

    /**
     * Gets a specific quote by index (for testing).
     */
    fun getQuote(index: Int): Quote {
        return quotes[index % quotes.size]
    }

    /**
     * Returns total number of quotes available.
     */
    fun getQuoteCount(): Int = quotes.size
}
